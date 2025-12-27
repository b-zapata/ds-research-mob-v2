package com.mindful.android.services.tracking

import android.app.Service.USAGE_STATS_SERVICE
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import com.mindful.android.helpers.storage.SharedPrefsHelper
import org.json.JSONObject

/**
 * Calculates intervention severity based on app usage within a rolling window.
 * Uses UsageStatsManager to query ACTIVITY_RESUMED/ACTIVITY_PAUSED events.
 */
class SeverityCalculator(private val context: Context) {
    companion object {
        private const val TAG = "Mindful.SeverityCalculator"
        
        // Default severity thresholds (fallback if StudyConfig not available)
        private const val DEFAULT_WINDOW_MINUTES = 15
        private const val DEFAULT_LEVEL1_MAX_OPEN_COUNT = 3
        private const val DEFAULT_LEVEL1_MAX_MINUTES = 5
        private const val DEFAULT_LEVEL2_MAX_OPEN_COUNT = 7
        private const val DEFAULT_LEVEL2_MAX_MINUTES = 20
    }

    private val usageStatsManager: UsageStatsManager =
        context.getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager

    data class SeverityThresholds(
        val windowMinutes: Int,
        val level1MaxOpenCount: Int,
        val level1MaxMinutes: Int,
        val level2MaxOpenCount: Int,
        val level2MaxMinutes: Int,
    )

    data class UsageMetrics(
        val openCount: Int,
        val totalForegroundMinutes: Int,
    )

    /**
     * Load severity thresholds from StudyConfig.parameters or use defaults
     */
    private fun loadSeverityThresholds(): SeverityThresholds {
        // Try to get StudyConfig from database via method channel
        // For now, use defaults - we can enhance this later to query Flutter
        return try {
            // TODO: Query Flutter for StudyConfig.parameters.severity
            // For MVP, use hardcoded defaults
            SeverityThresholds(
                windowMinutes = DEFAULT_WINDOW_MINUTES,
                level1MaxOpenCount = DEFAULT_LEVEL1_MAX_OPEN_COUNT,
                level1MaxMinutes = DEFAULT_LEVEL1_MAX_MINUTES,
                level2MaxOpenCount = DEFAULT_LEVEL2_MAX_OPEN_COUNT,
                level2MaxMinutes = DEFAULT_LEVEL2_MAX_MINUTES,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Error loading severity thresholds, using defaults", e)
            SeverityThresholds(
                windowMinutes = DEFAULT_WINDOW_MINUTES,
                level1MaxOpenCount = DEFAULT_LEVEL1_MAX_OPEN_COUNT,
                level1MaxMinutes = DEFAULT_LEVEL1_MAX_MINUTES,
                level2MaxOpenCount = DEFAULT_LEVEL2_MAX_OPEN_COUNT,
                level2MaxMinutes = DEFAULT_LEVEL2_MAX_MINUTES,
            )
        }
    }

    /**
     * Calculate usage metrics for a specific app within the rolling window
     */
    fun calculateUsageMetrics(packageName: String): UsageMetrics {
        val thresholds = loadSeverityThresholds()
        val endTime = System.currentTimeMillis()
        val startTime = endTime - (thresholds.windowMinutes * 60 * 1000L)

        Log.d(TAG, "calculateUsageMetrics: $packageName, window=${thresholds.windowMinutes}min, start=${startTime}, end=${endTime}")

        var openCount = 0
        var totalForegroundMs = 0L
        var lastResumedTime: Long? = null

        try {
            val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
            val event = UsageEvents.Event()

            while (usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(event)

                // Only process events for this specific package
                if (event.packageName != packageName) continue

                when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> {
                        openCount++
                        // If session started before window, clip to window start
                        val resumedTime = maxOf(event.timeStamp, startTime)
                        lastResumedTime = resumedTime
                        Log.d(TAG, "ACTIVITY_RESUMED at ${event.timeStamp} (clipped to $resumedTime)")
                    }
                    UsageEvents.Event.ACTIVITY_PAUSED,
                    UsageEvents.Event.ACTIVITY_STOPPED -> {
                        if (lastResumedTime != null) {
                            val pausedTime = event.timeStamp
                            // Clip paused time to window end if needed
                            val effectivePausedTime = minOf(pausedTime, endTime)
                            val sessionDuration = effectivePausedTime - lastResumedTime
                            if (sessionDuration > 0) {
                                totalForegroundMs += sessionDuration
                                Log.d(TAG, "ACTIVITY_PAUSED at $pausedTime, session duration=${sessionDuration}ms")
                            }
                            lastResumedTime = null
                        }
                    }
                }
            }

            // Handle case where app is still in foreground (RESUMED without PAUSED)
            if (lastResumedTime != null) {
                val effectiveEndTime = minOf(endTime, System.currentTimeMillis())
                val sessionDuration = effectiveEndTime - lastResumedTime
                if (sessionDuration > 0) {
                    totalForegroundMs += sessionDuration
                    Log.d(TAG, "App still in foreground, adding ${sessionDuration}ms to total")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying usage events", e)
        }

        val totalForegroundMinutes = (totalForegroundMs / (60 * 1000)).toInt()
        Log.d(TAG, "Usage metrics for $packageName: openCount=$openCount, totalForegroundMinutes=$totalForegroundMinutes")

        return UsageMetrics(openCount, totalForegroundMinutes)
    }

    /**
     * Determine intervention level (1, 2, or 3) based on usage metrics
     */
    fun calculateSeverityLevel(packageName: String): Int {
        val thresholds = loadSeverityThresholds()
        val metrics = calculateUsageMetrics(packageName)

        // Level 1 (mild): open_count ≤ level1.max_open_count AND total_foreground_time < level1.max_minutes
        if (metrics.openCount <= thresholds.level1MaxOpenCount &&
            metrics.totalForegroundMinutes < thresholds.level1MaxMinutes
        ) {
            Log.d(TAG, "Severity Level 1 for $packageName (openCount=${metrics.openCount}, minutes=${metrics.totalForegroundMinutes})")
            return 1
        }

        // Level 2 (moderate): open_count ≤ level2.max_open_count AND total_foreground_time < level2.max_minutes
        if (metrics.openCount <= thresholds.level2MaxOpenCount &&
            metrics.totalForegroundMinutes < thresholds.level2MaxMinutes
        ) {
            Log.d(TAG, "Severity Level 2 for $packageName (openCount=${metrics.openCount}, minutes=${metrics.totalForegroundMinutes})")
            return 2
        }

        // Level 3 (severe): anything above Level 2 thresholds
        Log.d(TAG, "Severity Level 3 for $packageName (openCount=${metrics.openCount}, minutes=${metrics.totalForegroundMinutes})")
        return 3
    }
}
