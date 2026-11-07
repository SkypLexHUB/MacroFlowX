package com.macroflow.automation.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import com.macroflow.automation.R
import com.macroflow.automation.domain.model.ActionType
import com.macroflow.automation.domain.model.MacroAction
import com.macroflow.automation.engine.ScreenSize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.hypot

/**
 * Records taps, long presses and swipes. A transparent layer captures each gesture, stores it,
 * then replays it to the app underneath so the app keeps responding while you record.
 * Pauses between gestures are stored as "wait before" values. The controls (stop) live in the
 * floating bar, which [MacroAccessibilityService] keeps above this layer.
 */
class RecorderOverlay(
    private val service: MacroAccessibilityService,
    private val scope: CoroutineScope,
    private val deps: ServiceEntryPoint,
    private val macroId: Long,
    private val onCount: (Int) -> Unit,
    private val onSaved: (Long) -> Unit,
    private val onFinished: () -> Unit,
) {
    private val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val density = service.resources.displayMetrics.density

    private var captureView: View? = null
    private var captureParams: WindowManager.LayoutParams? = null

    private val recorded = mutableListOf<MacroAction>()
    private var lastEnd = 0L
    private var busy = false
    private var tracking = false
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var finished = false

    private fun dp(v: Int) = (v * density).toInt()

    fun start(): Boolean {
        val capture = View(service).apply {
            setBackgroundColor(CAPTURE_TINT)
            setOnTouchListener { _, e ->
                handleTouch(e)
                true
            }
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        )
        return try {
            wm.addView(capture, lp)
            captureView = capture
            captureParams = lp
            lastEnd = SystemClock.uptimeMillis()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun handleTouch(e: MotionEvent) {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (busy) return
                downX = e.rawX
                downY = e.rawY
                downTime = SystemClock.uptimeMillis()
                tracking = true
            }
            MotionEvent.ACTION_UP -> {
                if (!tracking || busy) return
                tracking = false
                onGesture(downX, downY, e.rawX, e.rawY, downTime, SystemClock.uptimeMillis())
            }
            MotionEvent.ACTION_CANCEL -> tracking = false
        }
    }

    private fun onGesture(x1: Float, y1: Float, x2: Float, y2: Float, t1: Long, t2: Long) {
        if (recorded.size >= MAX_RECORDED) return
        val size = ScreenSize.of(service)
        val duration = t2 - t1
        val distance = hypot(x2 - x1, y2 - y1)
        val type = when {
            distance > dp(SWIPE_THRESHOLD_DP) -> ActionType.SWIPE
            duration >= LONG_PRESS_MS -> ActionType.LONG_PRESS
            else -> ActionType.TAP
        }
        val isSwipe = type == ActionType.SWIPE
        val action = MacroAction(
            type = type,
            x = (x1 / size.width).coerceIn(0f, 1f),
            y = (y1 / size.height).coerceIn(0f, 1f),
            endX = if (isSwipe) (x2 / size.width).coerceIn(0f, 1f) else (x1 / size.width).coerceIn(0f, 1f),
            endY = if (isSwipe) (y2 / size.height).coerceIn(0f, 1f) else (y1 / size.height).coerceIn(0f, 1f),
            durationMs = if (type == ActionType.TAP) 0 else duration.coerceIn(100L, 10_000L),
            delayMs = if (recorded.isEmpty()) 0 else (t1 - lastEnd).coerceIn(0L, 60_000L),
        )
        recorded.add(action)
        onCount(recorded.size)

        // Let the touch through to the app underneath by replaying the gesture.
        busy = true
        setCaptureTouchable(false)
        scope.launch {
            delay(REPLAY_SETTLE_MS)
            val replayDuration = if (type == ActionType.TAP) 50L else duration.coerceIn(100L, 10_000L)
            service.gesture(x1, y1, if (isSwipe) x2 else null, if (isSwipe) y2 else null, replayDuration)
            setCaptureTouchable(true)
            lastEnd = SystemClock.uptimeMillis()
            busy = false
        }
    }

    private fun setCaptureTouchable(touchable: Boolean) {
        val view = captureView ?: return
        val lp = captureParams ?: return
        lp.flags = if (touchable) {
            lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        } else {
            lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        try {
            wm.updateViewLayout(view, lp)
        } catch (_: Exception) {
        }
    }

    private fun removeViews() {
        captureView?.let {
            try {
                wm.removeView(it)
            } catch (_: Exception) {
            }
        }
        captureView = null
    }

    /** Ends the session. With [save] the recorded gestures are appended to the macro. */
    fun finish(save: Boolean) {
        if (finished) return
        finished = true
        removeViews()
        val toSave = recorded.toList()
        recorded.clear()
        if (save && toSave.isNotEmpty()) {
            scope.launch {
                try {
                    deps.macros().appendActions(macroId, toSave)
                    toast(service.getString(R.string.rec_saved, toSave.size))
                    onSaved(macroId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    toast(service.getString(R.string.error_generic))
                }
            }
        } else if (save) {
            toast(service.getString(R.string.rec_none))
        }
        onFinished()
    }

    private fun toast(message: String) {
        Toast.makeText(service, message, Toast.LENGTH_LONG).show()
    }

    private companion object {
        const val MAX_RECORDED = 500
        const val LONG_PRESS_MS = 500L
        const val SWIPE_THRESHOLD_DP = 24
        const val REPLAY_SETTLE_MS = 80L
        val CAPTURE_TINT = 0x22FF3B30
    }
}
