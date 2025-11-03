package com.mindful.android.services.tracking

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
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

    @Volatile private var currentForegroundPackage: String = ""

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

        val arm = readArm()
        Log.d(TAG, "Triggering intervention for $packageName arm=$arm")

        // Show overlay immediately (launch)
        overlayManager.showInterventionOverlay(packageName = packageName, arm = arm) {
            // Continue pressed – nothing else to do here
        }

        // Schedule retrigger if remains foreground
        scheduleRetrigger(minutes = cfg.retriggerMinutes, packageName = packageName, arm = arm)
    }

    fun onAppNoLongerForeground() {
        cancelRetrigger()
        currentForegroundPackage = ""
    }

    private fun scheduleRetrigger(minutes: Int, packageName: String, arm: String) {
        cancelRetrigger()
        if (minutes <= 0) return
        retriggerHandle = scheduler.scheduleAtFixedRate({
            // Ensure the same app is still considered foreground by LaunchTrackingManager
            if (currentForegroundPackage == packageName) {
                Handler(Looper.getMainLooper()).post {
                    overlayManager.showInterventionOverlay(packageName = packageName, arm = arm) {}
                }
            }
        }, minutes.toLong(), minutes.toLong(), TimeUnit.MINUTES)
        Log.d(TAG, "Retrigger scheduled every $minutes minutes for $packageName")
    }

    private fun cancelRetrigger() {
        retriggerHandle?.cancel(true)
        retriggerHandle = null
    }
}



