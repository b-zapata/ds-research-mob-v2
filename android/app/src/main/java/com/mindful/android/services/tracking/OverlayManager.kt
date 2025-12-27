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
import android.text.Editable
import android.text.TextWatcher
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
                    // Programmatic overlay (intervention): remove immediately (no animation delay)
                    windowManager.removeView(sheetOverlay)
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
            promptId = "", // Empty for placeholder
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
        promptId: String = "",
        promptText: String,
        expectedInteraction: String,
        minLockSeconds: Int,
        isTestMode: Boolean = false,
        onResponse: ((String) -> Unit)? = null,
        onContinue: (() -> Unit)? = null,
        onDismiss: (() -> Unit)? = null,
    ) {
        Log.d(TAG, "showInterventionOverlay: called for $packageName, interaction=$expectedInteraction, lockSeconds=$minLockSeconds, isTestMode=$isTestMode")
        
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

                // Root container (FrameLayout to allow cancel button to be positioned at bottom)
                val rootContainer = android.widget.FrameLayout(context).apply {
                    setBackgroundColor(0xFFFFFFFF.toInt())
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
                
                // ScrollView for scrollable content
                val scrollView = android.widget.ScrollView(context).apply {
                    setBackgroundColor(0xFFFFFFFF.toInt())
                    isFillViewport = true
                    layoutParams = android.widget.FrameLayout.LayoutParams(
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                    ).apply {
                        if (isTestMode) {
                            bottomMargin = 100 // Leave space for cancel button at bottom
                        }
                    }
                }
                
                val root = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setBackgroundColor(0xFFFFFFFF.toInt())
                    setPadding(64, 128, 64, 64)
                    isClickable = true
                    isFocusable = true
                    layoutParams = android.widget.FrameLayout.LayoutParams(
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                        android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
                    )
                }
                
                scrollView.addView(root)
                rootContainer.addView(scrollView)

                // Prompt text
                val promptTextView = android.widget.TextView(context).apply {
                    textSize = 18f
                    setTextColor(0xFF000000.toInt())
                    text = if (promptText.isNotEmpty()) promptText else "Intervention"
                    setPadding(0, 0, 0, 32)
                }

                // Interaction UI based on expectedInteraction type
                // Create a container for interaction view and done button
                val interactionContainer = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                }
                
                val (interactionView, doneButton) = createInteractionView(
                    promptId = promptId,
                    promptText = promptText,
                    expectedInteraction = expectedInteraction,
                    minLockSeconds = minLockSeconds,
                    onInteractionComplete = { response ->
                        onResponse?.invoke(response)
                        dismissSheetOverlay()
                        onContinue?.invoke()
                    }
                )

                if (interactionView != null) {
                    interactionContainer.addView(interactionView)
                }
                
                // Add done button if provided (for conditional completion)
                if (doneButton != null) {
                    val spacer = View(context).apply { minimumHeight = 24 }
                    interactionContainer.addView(spacer)
                    
                    // Create button container for Done and Cancel (if test mode)
                    val buttonContainer = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                    }
                    
                    doneButton.layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    buttonContainer.addView(doneButton)
                    
                    interactionContainer.addView(buttonContainer)
                }

                val spacer = View(context).apply { minimumHeight = 48 }

                // Continue button (for interactions that auto-complete like wait_out)
                val button = if (expectedInteraction.lowercase() == "wait_out") {
                    val buttonContainer = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                    }
                    
                    val continueBtn = android.widget.Button(context).apply {
                        text = "Continue"
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        setOnClickListener {
                            onResponse?.invoke("completed")
                            dismissSheetOverlay()
                            onContinue?.invoke()
                        }
                    }
                    buttonContainer.addView(continueBtn)
                    
                    buttonContainer
                } else {
                    null
                }

                root.addView(promptTextView)
                if (interactionContainer.childCount > 0) {
                    root.addView(interactionContainer)
                }
                root.addView(spacer)
                if (button != null) {
                    root.addView(button)
                }
                
                // Always add cancel button at the bottom in test mode (ensures it appears for all interaction types)
                if (isTestMode) {
                    Log.d(TAG, "showInterventionOverlay: Adding cancel button at bottom (test mode)")
                    val cancelButton = android.widget.Button(context).apply {
                        text = "Cancel"
                        visibility = android.view.View.VISIBLE
                        alpha = 1.0f
                        setBackgroundColor(0xFFE0E0E0.toInt())
                        setTextColor(0xFF000000.toInt())
                        setPadding(32, 16, 32, 16)
                        minimumWidth = 200
                        minimumHeight = 60
                        layoutParams = android.widget.FrameLayout.LayoutParams(
                            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            gravity = android.view.Gravity.BOTTOM
                            bottomMargin = 32
                            leftMargin = 64
                            rightMargin = 64
                        }
                        setOnClickListener {
                            Log.d(TAG, "showInterventionOverlay: Cancel button clicked")
                            // Dismiss immediately
                            dismissSheetOverlay()
                            // Report cancellation asynchronously (don't block UI)
                            Handler(Looper.getMainLooper()).post {
                                onDismiss?.invoke()
                            }
                        }
                    }
                    rootContainer.addView(cancelButton)
                    Log.d(TAG, "showInterventionOverlay: Cancel button added to rootContainer, visibility=${cancelButton.visibility}, alpha=${cancelButton.alpha}")
                }

                Log.d(TAG, "showInterventionOverlay: Showing intervention for $packageName")
                windowManager.addView(rootContainer, sheetLayoutParams)
                overlays.push(rootContainer)
                Utils.vibrateDevice(context, 30L)

                // Handle wait_out: auto-dismiss after minLockSeconds
                if (expectedInteraction == "wait_out" && minLockSeconds > 0) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (overlays.isNotEmpty() && overlays.peekFirst() == rootContainer) {
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
     * Returns a Pair of (interactionView, doneButton) where doneButton may be null
     * onInteractionComplete is called with the response value (e.g., "yes", "no", "completed")
     */
    private fun createInteractionView(
        promptId: String,
        promptText: String,
        expectedInteraction: String,
        minLockSeconds: Int,
        onInteractionComplete: (String) -> Unit
    ): Pair<View?, android.widget.Button?> {
        return when (expectedInteraction.lowercase()) {
            "yes_no" -> {
                // Yes/No buttons
                val view = LinearLayout(context).apply {
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
                Pair(view, null)
            }
            "slider" -> {
                createSliderInteraction(promptId, promptText, onInteractionComplete)
            }
            "tap_hold" -> {
                // Tap and hold button - auto-completes when held
                val view = android.widget.Button(context).apply {
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
                Pair(view, null)
            }
            "tap" -> {
                createTapInteraction(promptId, promptText, onInteractionComplete)
            }
            "tap_sequence" -> {
                createTapSequenceInteraction(promptId, promptText, onInteractionComplete)
            }
            "type_phrase" -> {
                createTypePhraseInteraction(promptId, promptText, onInteractionComplete)
            }
            "combo" -> {
                createComboInteraction(promptId, promptText, minLockSeconds, onInteractionComplete)
            }
            else -> Pair(null, null) // wait_out, etc. - no special UI
        }
    }

    /**
     * Create slider interaction with conditional done button based on prompt requirements
     */
    private fun createSliderInteraction(
        promptId: String,
        promptText: String,
        onInteractionComplete: (String) -> Unit
    ): Pair<View, android.widget.Button> {
        val slider = android.widget.SeekBar(context).apply {
            max = 100
            progress = 0
        }
        
        val doneButton = android.widget.Button(context).apply {
            text = "Done"
            isEnabled = false
            setOnClickListener {
                onInteractionComplete("completed")
            }
        }
        
        // Track state for multi-slide prompts
        var slideCount = 0
        var unlockCount = 0
        var lastProgress = 0
        
        slider.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val isValid = when (promptId) {
                        "friction_1_2", "friction_1_12", "friction_2_1", "friction_3_1" -> {
                            // Must reach 100%
                            progress >= 100
                        }
                        "friction_1_6" -> {
                            // Must slide left to right twice
                            if (progress > lastProgress && progress >= 100) {
                                slideCount++
                                lastProgress = 0
                                if (slideCount >= 2) {
                                    true
                                } else {
                                    Handler(Looper.getMainLooper()).post {
                                        slider.progress = 0 // Reset for next slide
                                    }
                                    false
                                }
                            } else {
                                lastProgress = progress
                                false
                            }
                        }
                        "friction_1_9" -> {
                            // Must be at midpoint (45-55)
                            progress in 45..55
                        }
                        "friction_2_5" -> {
                            // Must slide to unlock three times
                            if (progress > lastProgress && progress >= 100) {
                                unlockCount++
                                lastProgress = 0
                                if (unlockCount >= 3) {
                                    true
                                } else {
                                    Handler(Looper.getMainLooper()).post {
                                        slider.progress = 0 // Reset for next unlock
                                    }
                                    false
                                }
                            } else {
                                lastProgress = progress
                                false
                            }
                        }
                        else -> {
                            // Default: must reach 100%
                            progress >= 100
                        }
                    }
                    doneButton.isEnabled = isValid
                }
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })
        
        return Pair(slider, doneButton)
    }

    /**
     * Create tap interaction with conditional done button
     */
    private fun createTapInteraction(
        promptId: String,
        promptText: String,
        onInteractionComplete: (String) -> Unit
    ): Pair<View, android.widget.Button> {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        
        val doneButton = android.widget.Button(context).apply {
            text = "Done"
            isEnabled = false
            setOnClickListener {
                onInteractionComplete("completed")
            }
        }
        
        when (promptId) {
            "friction_1_3" -> {
                // Tap the highlighted shape - show shapes, one highlighted
                val shapes = listOf("○", "□", "△", "◇")
                val correctIndex = 1 // Highlighted shape
                val shapeContainer = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER
                }
                
                shapes.forEachIndexed { index, shape ->
                    val shapeBtn = android.widget.Button(context).apply {
                        text = shape
                        setPadding(32, 16, 32, 16)
                        if (index == correctIndex) {
                            setBackgroundColor(0xFF4CAF50.toInt()) // Green for highlighted
                            setTextColor(0xFFFFFFFF.toInt())
                        }
                        setOnClickListener {
                            if (index == correctIndex) {
                                doneButton.isEnabled = true
                            }
                        }
                    }
                    shapeContainer.addView(shapeBtn)
                }
                container.addView(shapeContainer)
            }
            "friction_1_7" -> {
                // Tap the moving circle when it turns green
                val circleBtn = android.widget.Button(context).apply {
                    text = "○"
                    setPadding(64, 64, 64, 64)
                    setBackgroundColor(0xFF2196F3.toInt()) // Blue initially
                    setTextColor(0xFFFFFFFF.toInt())
                    
                    // Change to green after delay
                    Handler(Looper.getMainLooper()).postDelayed({
                        setBackgroundColor(0xFF4CAF50.toInt()) // Green
                        setOnClickListener {
                            doneButton.isEnabled = true
                        }
                    }, 2000L) // Turn green after 2 seconds
                }
                container.addView(circleBtn)
            }
            "friction_2_3" -> {
                // Solve: 8 + 5 = ? - Show multiple choice
                val questionText = android.widget.TextView(context).apply {
                    text = "8 + 5 = ?"
                    textSize = 20f
                    gravity = android.view.Gravity.CENTER
                    setPadding(0, 16, 0, 16)
                }
                container.addView(questionText)
                
                val answers = listOf("10", "12", "13", "15")
                val correctAnswer = "13"
                val answerContainer = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER
                }
                
                answers.forEach { answer ->
                    val answerBtn = android.widget.Button(context).apply {
                        text = answer
                        setPadding(32, 16, 32, 16)
                        setOnClickListener {
                            if (answer == correctAnswer) {
                                doneButton.isEnabled = true
                            }
                        }
                    }
                    answerContainer.addView(answerBtn)
                }
                container.addView(answerContainer)
            }
        }
        
        return Pair(container, doneButton)
    }

    /**
     * Create tap sequence interaction with conditional done button
     */
    private fun createTapSequenceInteraction(
        promptId: String,
        promptText: String,
        onInteractionComplete: (String) -> Unit
    ): Pair<View, android.widget.Button> {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        
        val doneButton = android.widget.Button(context).apply {
            text = "Done"
            isEnabled = false
            setOnClickListener {
                onInteractionComplete("completed")
            }
        }
        
        // Determine correct sequence based on promptId
        val correctSequence = when (promptId) {
            "friction_2_2" -> listOf(3, 1, 2) // "Tap the sequence: 3–1–2"
            else -> listOf(1, 2, 3, 4) // Default sequence
        }
        
        val sequenceText = android.widget.TextView(context).apply {
            text = if (promptId == "friction_2_2") "Tap: 3 → 1 → 2" else "Tap in the correct order"
            textSize = 16f
            gravity = android.view.Gravity.CENTER
            setPadding(0, 16, 0, 16)
        }
        container.addView(sequenceText)
        
        val items = when (promptId) {
            "friction_1_5" -> listOf("🔴", "🔵", "🟢", "🟡") // Color pairs
            "friction_1_10" -> listOf("A", "B", "C", "D") // Symbols
            "friction_2_7" -> listOf("●", "●●", "●●●", "●●●●") // Shapes by size
            "friction_3_4" -> listOf("★", "◆", "●", "■") // Symbols
            else -> listOf("1", "2", "3", "4")
        }
        
        val itemContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
        }
        
        val tappedSequence = mutableListOf<Int>()
        
        items.forEachIndexed { index, item ->
            val itemBtn = android.widget.Button(context).apply {
                text = item
                setPadding(32, 16, 32, 16)
                setOnClickListener {
                    tappedSequence.add(index)
                    // Check if sequence matches
                    if (tappedSequence.size == correctSequence.size) {
                        val matches = tappedSequence.mapIndexed { i, value -> 
                            correctSequence[i] == (value + 1) // Convert 0-indexed to 1-indexed
                        }.all { it }
                        if (matches) {
                            doneButton.isEnabled = true
                        } else {
                            // Reset on wrong sequence
                            tappedSequence.clear()
                            items.forEachIndexed { idx, _ ->
                                (itemContainer.getChildAt(idx) as? android.widget.Button)?.isEnabled = true
                            }
                        }
                    }
                }
            }
            itemContainer.addView(itemBtn)
        }
        
        container.addView(itemContainer)
        
        return Pair(container, doneButton)
    }

    /**
     * Create type phrase interaction with real-time validation and conditional done button
     */
    private fun createTypePhraseInteraction(
        promptId: String,
        promptText: String,
        onInteractionComplete: (String) -> Unit
    ): Pair<View, android.widget.Button> {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        
        // Extract expected phrase from prompt text
        val expectedPhrase = when (promptId) {
            "friction_2_6" -> "pause before scroll"
            "friction_3_3" -> "I choose focus now."
            else -> ""
        }
        
        val instructionText = android.widget.TextView(context).apply {
            text = "Type: \"$expectedPhrase\""
            textSize = 16f
            gravity = android.view.Gravity.CENTER
            setPadding(0, 16, 0, 16)
        }
        container.addView(instructionText)
        
        val editText = android.widget.EditText(context).apply {
            hint = "Type here..."
            textSize = 16f
            setPadding(16, 16, 16, 16)
            setBackgroundColor(0xFFF5F5F5.toInt())
        }
        container.addView(editText)
        
        val doneButton = android.widget.Button(context).apply {
            text = "Done"
            isEnabled = false
            setOnClickListener {
                onInteractionComplete("completed")
            }
        }
        
        // Real-time validation
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val inputText = s?.toString()?.lowercase()?.trim() ?: ""
                val isMatch = inputText == expectedPhrase.lowercase().trim()
                doneButton.isEnabled = isMatch
            }
        })
        
        return Pair(container, doneButton)
    }

    /**
     * Create combo interaction with multi-step validation
     */
    private fun createComboInteraction(
        promptId: String,
        promptText: String,
        minLockSeconds: Int,
        onInteractionComplete: (String) -> Unit
    ): Pair<View, android.widget.Button> {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        
        val instructionText = android.widget.TextView(context).apply {
            text = "Complete: Slider → Tap"
            textSize = 16f
            gravity = android.view.Gravity.CENTER
            setPadding(0, 16, 0, 16)
        }
        container.addView(instructionText)
        
        var sliderCompleted = false
        var tapCompleted = false
        
        val step1Text = android.widget.TextView(context).apply {
            text = "Step 1: Slide to 100%"
            textSize = 14f
            setPadding(0, 8, 0, 8)
        }
        
        val step2Text = android.widget.TextView(context).apply {
            text = "Step 2: Tap"
            textSize = 14f
            setPadding(0, 8, 0, 8)
        }
        
        val doneButton = android.widget.Button(context).apply {
            text = "Done"
            isEnabled = false
            setOnClickListener {
                onInteractionComplete("completed")
            }
        }
        
        // Update function to check completion
        val updateDoneButton: () -> Unit = {
            doneButton.isEnabled = sliderCompleted && tapCompleted
            step1Text.text = "Step 1: Slide to 100% ${if (sliderCompleted) "✓" else ""}"
            step2Text.text = "Step 2: Tap ${if (tapCompleted) "✓" else ""}"
        }
        
        // Step 1: Slider
        val slider = android.widget.SeekBar(context).apply {
            max = 100
            progress = 0
            setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser && progress >= 100) {
                        sliderCompleted = true
                        updateDoneButton()
                    }
                }
                override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
            })
        }
        container.addView(slider)
        container.addView(step1Text)
        
        // Step 2: Tap
        val tapButton = android.widget.Button(context).apply {
            text = "Tap Here"
            setOnClickListener {
                tapCompleted = true
                updateDoneButton()
            }
        }
        container.addView(tapButton)
        container.addView(step2Text)
        
        return Pair(container, doneButton)
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
