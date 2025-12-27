package com.mindful.android.services.tracking

import android.app.NotificationManager
import android.app.Service.NOTIFICATION_SERVICE
import android.content.Context
import android.content.Context.WINDOW_SERVICE
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.toBitmap
import com.mindful.android.R
import com.mindful.android.helpers.device.NotificationHelper
import com.mindful.android.helpers.device.NotificationHelper.USAGE_REMINDERS_CHANNEL_ID
import com.mindful.android.helpers.storage.SharedPrefsHelper
import com.mindful.android.models.RestrictionState
import com.mindful.android.services.accessibility.MindfulAccessibilityService
import com.mindful.android.services.accessibility.MindfulAccessibilityService.Companion.ACTION_PERFORM_HOME_PRESS
import com.mindful.android.services.tracking.OverlayBuilder.getAppLabelAndIcon
import com.mindful.android.utils.AppUtils
import com.mindful.android.utils.DateTimeUtils
import com.mindful.android.utils.ThreadUtils
import com.mindful.android.utils.Utils
import java.util.concurrent.ConcurrentLinkedDeque


class OverlayManager(
    private val context: Context,
) {
    private var overlays: ConcurrentLinkedDeque<View> = ConcurrentLinkedDeque()
    private val windowManager: WindowManager =
        context.getSystemService(WINDOW_SERVICE) as WindowManager


    /// Animate out the overlay
    fun dismissSheetOverlay() {
        overlays.pollFirst()?.let { sheetOverlay ->
            ThreadUtils.runOnMainThread {
                // Get views (may be null for programmatic intervention overlays)
                val bg = sheetOverlay.findViewById<View>(R.id.overlay_background)
                val quote = sheetOverlay.findViewById<View>(R.id.overlay_sheet_quote_panel)
                val sheet = sheetOverlay.findViewById<LinearLayout>(R.id.overlay_sheet)

                // Check if this is an XML-based overlay (has the expected views)
                if (bg != null && quote != null && sheet != null) {
                    // XML-based overlay: animate out
                    bg.animate().alpha(0f).setDuration(400).start()
                    quote.animate().alpha(0f).setDuration(400).start()
                    sheet.animate()
                        .translationY(SLIDE_DOWN_END_Y)
                        .setInterpolator(OvershootInterpolator(1.5f))
                        .setDuration(500)
                        .withEndAction {
                            windowManager.removeView(sheetOverlay)
                        }
                        .start()
                } else {
                    // Programmatic overlay (intervention): simple fade out and remove
                    sheetOverlay.animate()
                        .alpha(0f)
                        .setDuration(300)
                        .withEndAction {
                            windowManager.removeView(sheetOverlay)
                        }
                        .start()
                }
            }
        }
    }

    /**
     * Shows a full screen, non-dismissable overlay for Intervention placeholders.
     * Simple programmatic UI to avoid XML changes.
     * arm: blank|mindfulness|friction|identity
     * 
     * Legacy method for backward compatibility
     */
    fun showInterventionOverlay(
        packageName: String,
        arm: String,
        onContinue: (() -> Unit)? = null,
    ) {
        showInterventionOverlay(
            packageName = packageName,
            promptText = "", // Empty for placeholder
            expectedInteraction = "wait_out",
            minLockSeconds = 5,
            onResponse = null, // Placeholder doesn't need response
            onContinue = onContinue,
            onDismiss = null
        )
    }

    /**
     * Shows intervention overlay with actual prompt text and interaction handling
     */
    fun showInterventionOverlay(
        packageName: String,
        promptText: String,
        expectedInteraction: String,
        minLockSeconds: Int,
        onResponse: ((String) -> Unit)? = null,
        onContinue: (() -> Unit)? = null,
        onDismiss: (() -> Unit)? = null,
    ) {
        Log.d(TAG, "showInterventionOverlay: called for $packageName, interaction=$expectedInteraction, lockSeconds=$minLockSeconds")
        
        // Return if overlay is not null
        if (overlays.isNotEmpty()) {
            Log.d(TAG, "showInterventionOverlay: Overlay already exists, skipping")
            return
        }

        ThreadUtils.runOnMainThread {
            runCatching {
                if (!haveOverlayPermission(context)) {
                    Log.w(TAG, "showInterventionOverlay: No overlay permission!")
                    return@runOnMainThread
                }

                val root = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setBackgroundColor(0xFFFFFFFF.toInt())
                    setPadding(64, 128, 64, 64)
                    isClickable = true
                    isFocusable = true
                }

                // Prompt text
                val promptTextView = android.widget.TextView(context).apply {
                    textSize = 18f
                    setTextColor(0xFF000000.toInt())
                    text = if (promptText.isNotEmpty()) promptText else "Intervention"
                    setPadding(0, 0, 0, 32)
                }

                // Interaction UI based on expectedInteraction type
                val interactionView = createInteractionView(
                    expectedInteraction = expectedInteraction,
                    minLockSeconds = minLockSeconds,
                    onInteractionComplete = { response ->
                        onResponse?.invoke(response)
                        dismissSheetOverlay()
                        onContinue?.invoke()
                    }
                )

                val spacer = View(context).apply { minimumHeight = 48 }

                // Continue button (for interactions that don't auto-complete)
                // Only show if interaction doesn't auto-complete (like wait_out)
                val button = if (expectedInteraction.lowercase() == "wait_out") {
                    android.widget.Button(context).apply {
                        text = "Continue"
                        setOnClickListener {
                            onResponse?.invoke("completed")
                            dismissSheetOverlay()
                            onContinue?.invoke()
                        }
                    }
                } else {
                    null
                }

                root.addView(promptTextView)
                if (interactionView != null) {
                    root.addView(interactionView)
                }
                root.addView(spacer)
                if (button != null) {
                    root.addView(button)
                }

                Log.d(TAG, "showInterventionOverlay: Showing intervention for $packageName")
                windowManager.addView(root, sheetLayoutParams)
                overlays.push(root)
                Utils.vibrateDevice(context, 30L)

                // Handle wait_out: auto-dismiss after minLockSeconds
                if (expectedInteraction == "wait_out" && minLockSeconds > 0) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (overlays.isNotEmpty() && overlays.peekFirst() == root) {
                            onResponse?.invoke("completed")
                            dismissSheetOverlay()
                            onContinue?.invoke()
                        }
                    }, minLockSeconds * 1000L)
                }
            }.getOrElse {
                SharedPrefsHelper.insertCrashLogToPrefs(context, it)
            }
        }
    }

    /**
     * Create interaction-specific UI based on expectedInteraction type
     * Returns null if no special UI needed (just show prompt text)
     * onInteractionComplete is called with the response value (e.g., "yes", "no", "75", "completed")
     */
    private fun createInteractionView(
        expectedInteraction: String,
        minLockSeconds: Int,
        onInteractionComplete: (String) -> Unit
    ): View? {
        return when (expectedInteraction.lowercase()) {
            "yes_no" -> {
                // Yes/No buttons
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    val yesBtn = android.widget.Button(context).apply {
                        text = "Yes"
                        setOnClickListener { onInteractionComplete("yes") }
                    }
                    val noBtn = android.widget.Button(context).apply {
                        text = "No"
                        setOnClickListener { onInteractionComplete("no") }
                    }
                    addView(yesBtn)
                    addView(noBtn)
                }
            }
            "slider" -> {
                // Slider interaction
                var hasCompleted = false
                android.widget.SeekBar(context).apply {
                    max = 100
                    progress = 0
                    setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                            if (progress >= 100 && !hasCompleted) {
                                hasCompleted = true
                                onInteractionComplete("100")
                            }
                        }
                        override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
                        override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {
                            // Capture final value when user releases (if not already completed)
                            seekBar?.let {
                                if (!hasCompleted && it.progress < 100) {
                                    hasCompleted = true
                                    onInteractionComplete(it.progress.toString())
                                }
                            }
                        }
                    })
                }
            }
            "tap_hold" -> {
                // Tap and hold button
                android.widget.Button(context).apply {
                    text = "Hold to Continue"
                    setOnTouchListener { v, event ->
                        when (event.action) {
                            android.view.MotionEvent.ACTION_DOWN -> {
                                // Start hold timer
                                Handler(Looper.getMainLooper()).postDelayed({
                                    if (v.isPressed) {
                                        onInteractionComplete("completed")
                                    }
                                }, minLockSeconds * 1000L)
                                true
                            }
                            android.view.MotionEvent.ACTION_UP -> {
                                v.isPressed = false
                                true
                            }
                            else -> false
                        }
                    }
                }
            }
            else -> null // wait_out, tap, etc. - no special UI
        }
    }

    fun showSheetOverlay(
        packageName: String,
        restrictionState: RestrictionState,
        addReminderWithDelay: ((futureMinutes: Int) -> Unit)? = null,
    ) {
        // Return if overlay is not null
        if (overlays.isNotEmpty()) return

        ThreadUtils.runOnMainThread {
            runCatching {

                // Notify, stop and return if don't have overlay permission
                if (!haveOverlayPermission(context)) {
                    return@runOnMainThread
                }

                // Build overlay
                val sheetOverlay = OverlayBuilder.buildFullScreenOverlay(
                    context = context,
                    packageName = packageName,
                    state = restrictionState,
                    dismissOverlay = ::dismissSheetOverlay,
                    addReminderDelay = addReminderWithDelay,
                ).apply {
                    // TODO: Fix the deprecated logic
                    // Full screen edge to edge view
                    systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                }

                Log.d(TAG, "showFullScreenOverlay: Showing full screen overlay for $packageName")
                sheetOverlay.also {
                    windowManager.addView(it, sheetLayoutParams)
                    overlays.push(it)
                    Utils.vibrateDevice(context, 50L)

                    // Get views
                    val bg = sheetOverlay.findViewById<View>(R.id.overlay_background)
                    val quote = sheetOverlay.findViewById<View>(R.id.overlay_sheet_quote_panel)
                    val sheet = sheetOverlay.findViewById<LinearLayout>(R.id.overlay_sheet)

                    // Set initial
                    bg.alpha = 0f
                    quote.alpha = 0f
                    sheet.translationY = SLIDE_UP_START_Y

                    // Animate
                    bg.animate().alpha(1f).setDuration(400).start()
                    quote.animate().alpha(1f).setDuration(400).start()
                    sheet.animate()
                        .translationY(0f)
                        .setInterpolator(OvershootInterpolator(1.5f))
                        .setDuration(500)
                        .start()
                }
            }.getOrElse {
                SharedPrefsHelper.insertCrashLogToPrefs(context, it)
            }
        }
    }


    fun showToastOverlay(
        packageName: String,
        screenTimeUsedInMins: Int,
    ) {
        ThreadUtils.runOnMainThread {
            runCatching {
                // Notify, stop and return if don't have overlay permission
                if (!haveOverlayPermission(context)) return@runOnMainThread

                // Build view
                val toastView = OverlayBuilder.buildToastOverlay(
                    context,
                    packageName,
                    screenTimeUsedInMins
                )

                Log.d(TAG, "Showing toast overlay for $packageName")
                windowManager.addView(toastView, toastLayoutParams)

                // Fade-in
                toastView.animate()
                    .alpha(1f)
                    .setDuration(500)
                    .start()

                // Fade-out and remove after delay
                Handler(Looper.getMainLooper()).let {
                    it.postDelayed({
                        toastView.animate()
                            .alpha(0f)
                            .setDuration(500)
                            .withEndAction {
                                it.postDelayed({ windowManager.removeView(toastView) }, 100L)
                            }
                            .start()
                    }, 5000)
                }
            }.getOrElse { e ->
                Log.e(TAG, "showToastOverlay: Failed to show toast overlay", e)
                SharedPrefsHelper.insertCrashLogToPrefs(context, e)
            }
        }
    }

    fun showNotification(
        packageName: String,
        screenTimeUsedInMins: Int,
    ) {
        // Get notification manager
        val notificationManager =
            context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        // Resolve app icon and label
        val (appName, appIcon) = getAppLabelAndIcon(context, packageName)

        val msg = context.getString(
            R.string.app_screen_time_usage_info,
            DateTimeUtils.minutesToTimeStr(screenTimeUsedInMins)
        )
        notificationManager.notify(
            302,
            NotificationCompat.Builder(
                context,
                USAGE_REMINDERS_CHANNEL_ID
            )
                .setSmallIcon(R.drawable.ic_mindful_notification)
                .setLargeIcon(appIcon.toBitmap())
                .setContentTitle(appName)
                .setContentText(msg)
                .setStyle(NotificationCompat.BigTextStyle().bigText(msg))
                .setSound(null)
                .setAutoCancel(true)
                .setContentIntent(
                    AppUtils.getPendingIntentForMindfulUri(
                        context,
                        "com.mindful.android://open/appDashboard?package=$packageName"
                    )
                )
                .build()
        )

    }

    companion object {
        private const val TAG = "Mindful.OverlayManager"

        private const val SLIDE_UP_START_Y = 640f
        private const val SLIDE_DOWN_END_Y = 1280f

        private val sheetLayoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        }

        private val toastLayoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            verticalMargin = 0.05f
        }


        private fun haveOverlayPermission(context: Context): Boolean {
            if (!Settings.canDrawOverlays(context)) {
                // Show notification
                NotificationHelper.pushAskOverlayPermissionNotification(context)

                // Go home if accessibility is running
                if (Utils.isServiceRunning(context, MindfulAccessibilityService::class.java)) {
                    val serviceIntent = Intent(
                        context.applicationContext,
                        MindfulAccessibilityService::class.java
                    ).setAction(ACTION_PERFORM_HOME_PRESS)

                    context.startService(serviceIntent)
                }

                Log.d(
                    TAG,
                    "checkOverlayPermission: Display overlay permission denied, returning"
                )
                return false
            } else {
                return true
            }
        }
    }
}
