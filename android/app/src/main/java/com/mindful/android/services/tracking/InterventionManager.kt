package com.mindful.android.services.tracking

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.mindful.android.helpers.FlutterMethodChannelHelper
import com.mindful.android.helpers.storage.SharedPrefsHelper
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Lightweight intervention engine on Android side reusing existing foreground tracking and overlays.
 * - Loads flaggedPackages and retriggerMinutes from asset/SharedPrefs
 * - Reads participant arm from SharedPrefs (key: participant_arm). Defaults to "blank".
 * - Shows a full-screen, non-dismissable overlay with a simple placeholder for each arm.
 * - Retriggers every X minutes while the same app stays in foreground.
 */
class InterventionManager(
    private val context: Context,
    private val overlayManager: OverlayManager,
) {
    companion object {
        private const val TAG = "Mindful.InterventionManager"
        private const val PREFS_CONFIG_JSON = "intervention_config_json"
        private const val PREFS_PARTICIPANT_ARM = "participant_arm" // blank|mindfulness|friction|identity
        private const val DEFAULT_RETRIGGER_MINUTES = 5
    }

    init {
        Log.d(TAG, "InterventionManager initialized")
    }

    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private var retriggerHandle: ScheduledFuture<*>? = null
    private val severityCalculator = SeverityCalculator(context)

    @Volatile private var currentForegroundPackage: String = ""
    
    // Track current intervention state
    private var currentSessionId: Int? = null
    private var currentPromptDeliveryLogId: Int? = null
    private var interventionStartTime: Long = 0

    private data class Config(
        val flaggedPackages: Set<String>,
        val retriggerMinutes: Int,
    )

    private fun loadConfig(): Config {
        // Try SharedPrefs first for runtime updates
        val prefsJson = SharedPrefsHelper.getString(context, PREFS_CONFIG_JSON, null)
        val jsonString = prefsJson ?: runCatching {
            context.assets.open("intervention_config.json").bufferedReader().use { it.readText() }
        }.getOrNull()

        if (jsonString != null) {
            runCatching {
                val o = JSONObject(jsonString)
                val flagged = mutableSetOf<String>()
                val arr = o.optJSONArray("flaggedPackages")
                if (arr != null) {
                    for (i in 0 until arr.length()) flagged.add(arr.optString(i))
                }
                val minutes = o.optInt("retriggerMinutes", DEFAULT_RETRIGGER_MINUTES)
                Log.d(TAG, "loadConfig: Loaded ${flagged.size} flagged packages: $flagged, retriggerMinutes=$minutes")
                return Config(flagged, minutes)
            }.onFailure { Log.w(TAG, "Failed to parse intervention_config.json", it) }
        } else {
            Log.w(TAG, "loadConfig: Could not load intervention_config.json from assets or SharedPrefs")
        }
        // Fallback defaults
        Log.d(TAG, "loadConfig: Using fallback config")
        return Config(setOf("com.instagram.android"), DEFAULT_RETRIGGER_MINUTES)
    }

    private fun readArm(): String {
        return SharedPrefsHelper.getString(context, PREFS_PARTICIPANT_ARM, "blank") ?: "blank"
    }

    /**
     * Checks if interventions are enabled (i.e., has any flagged packages).
     * Used to determine if tracker service should keep running.
     */
    fun hasInterventionsEnabled(): Boolean {
        val cfg = loadConfig()
        return cfg.flaggedPackages.isNotEmpty()
    }

    fun onNewAppLaunched(packageName: String) {
        val cfg = loadConfig()
        currentForegroundPackage = packageName
        Log.d(TAG, "onNewAppLaunched: $packageName (flagged=${cfg.flaggedPackages.contains(packageName)})")

        if (!cfg.flaggedPackages.contains(packageName)) {
            Log.d(TAG, "onNewAppLaunched: $packageName not in flagged list, cancelling retrigger")
            cancelRetrigger()
            return
        }

        // Calculate severity level
        val level = severityCalculator.calculateSeverityLevel(packageName)
        Log.d(TAG, "Triggering intervention for $packageName level=$level")

        // Call Flutter to get prompt
        triggerIntervention(packageName, level, "launch")

        // Schedule retrigger if remains foreground
        scheduleRetrigger(minutes = cfg.retriggerMinutes, packageName = packageName, level = level)
    }
    
    /**
     * Trigger intervention by calling Flutter for prompt and displaying overlay
     */
    private fun triggerIntervention(packageName: String, level: Int, triggerType: String) {
        // Call Flutter to get prompt
        val args = mapOf(
            "level" to level,
            "appPackage" to packageName
        )
        
        val promptResult = FlutterMethodChannelHelper.invokeMethod("getPromptForIntervention", args)
        
        if (promptResult == null) {
            Log.w(TAG, "Failed to get prompt from Flutter, showing placeholder")
            val arm = readArm()
            Handler(Looper.getMainLooper()).post {
                overlayManager.showInterventionOverlay(packageName = packageName, arm = arm) {
                    // Continue pressed
                }
            }
            return
        }

        // Extract prompt data
        val sessionId = (promptResult["sessionId"] as? Number)?.toInt()
        val promptDeliveryLogId = (promptResult["promptDeliveryLogId"] as? Number)?.toInt()
        val promptId = promptResult["promptId"] as? String ?: ""
        val promptText = promptResult["text"] as? String ?: ""
        val expectedInteraction = promptResult["expectedInteraction"] as? String ?: "wait_out"
        val minLockSeconds = (promptResult["minLockSeconds"] as? Number)?.toInt() ?: 5

        // Store for completion callback
        currentSessionId = sessionId
        currentPromptDeliveryLogId = promptDeliveryLogId
        interventionStartTime = System.currentTimeMillis()

        Log.d(TAG, "Got prompt from Flutter: sessionId=$sessionId, promptId=$promptId, interaction=$expectedInteraction")

        // Show overlay with actual prompt (must be on main thread)
        Handler(Looper.getMainLooper()).post {
            overlayManager.showInterventionOverlay(
                packageName = packageName,
                promptText = promptText,
                expectedInteraction = expectedInteraction,
                minLockSeconds = minLockSeconds,
                onResponse = { response ->
                    // Capture response and report completion
                    reportInterventionCompletion(
                        success = true,
                        outcome = "completed",
                        responseContent = response
                    )
                },
                onContinue = {
                    // This is called after onResponse, so we don't need to do anything here
                    // The response has already been captured
                },
                onDismiss = {
                    // Intervention dismissed/skipped (no response)
                    reportInterventionCompletion(
                        success = false,
                        outcome = "skipped",
                        responseContent = ""
                    )
                }
            )
        }
    }
    
    /**
     * Report intervention completion to Flutter
     */
    private fun reportInterventionCompletion(
        success: Boolean,
        outcome: String,
        responseContent: String = ""
    ) {
        val sessionId = currentSessionId
        val promptDeliveryLogId = currentPromptDeliveryLogId
        
        if (sessionId == null || promptDeliveryLogId == null) {
            Log.w(TAG, "Cannot report completion: missing sessionId or promptDeliveryLogId")
            return
        }

        val secondsSpent = ((System.currentTimeMillis() - interventionStartTime) / 1000).toInt()
        
        val args = mapOf(
            "sessionId" to sessionId,
            "promptDeliveryLogId" to promptDeliveryLogId,
            "success" to success,
            "outcome" to outcome,
            "secondsSpent" to secondsSpent,
            "responseContent" to responseContent
        )
        
        FlutterMethodChannelHelper.invokeMethod("reportInterventionCompleted", args)
        
        // Clear state
        currentSessionId = null
        currentPromptDeliveryLogId = null
        interventionStartTime = 0
    }

    fun onAppNoLongerForeground() {
        cancelRetrigger()
        currentForegroundPackage = ""
    }

    private fun scheduleRetrigger(minutes: Int, packageName: String, level: Int) {
        cancelRetrigger()
        if (minutes <= 0) return
        retriggerHandle = scheduler.scheduleAtFixedRate({
            // Ensure the same app is still considered foreground by LaunchTrackingManager
            if (currentForegroundPackage == packageName) {
                // Recalculate severity for retrigger (may have changed) - runs on scheduler thread
                val currentLevel = severityCalculator.calculateSeverityLevel(packageName)
                triggerIntervention(packageName, currentLevel, "retrigger")
            }
        }, minutes.toLong(), minutes.toLong(), TimeUnit.MINUTES)
        Log.d(TAG, "Retrigger scheduled every $minutes minutes for $packageName")
    }

    private fun cancelRetrigger() {
        retriggerHandle?.cancel(true)
        retriggerHandle = null
    }
}



