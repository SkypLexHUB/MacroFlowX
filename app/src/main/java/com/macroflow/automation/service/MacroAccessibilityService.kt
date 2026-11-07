package com.macroflow.automation.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.macroflow.automation.R
import com.macroflow.automation.domain.model.ActionType
import com.macroflow.automation.domain.model.MacroAction
import com.macroflow.automation.engine.PlayerStatus
import com.macroflow.automation.engine.ScreenSize
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Performs taps and swipes for macros and hosts the floating controller and the recorder overlays.
 * It declares no event types and cannot read window content, so it never sees what is on screen.
 */
class MacroAccessibilityService : AccessibilityService() {

    private var scope: CoroutineScope? = null
    private var deps: ServiceEntryPoint? = null
    private var floating: FloatingController? = null
    private var recorder: RecorderOverlay? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        val entry = EntryPointAccessors.fromApplication(applicationContext, ServiceEntryPoint::class.java)
        val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        deps = entry
        scope = serviceScope
        floating = FloatingController(this, serviceScope, entry).also { it.start() }
        publish(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        shutdown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    private fun shutdown() {
        deps?.player()?.stop()
        recorder?.finish(save = false)
        recorder = null
        floating?.stop()
        floating = null
        scope?.cancel()
        scope = null
        deps = null
        if (_instance.value === this) publish(null)
    }

    /** Starts the recorder overlay for [macroId]. Returns false if it could not start. */
    fun startRecording(macroId: Long): Boolean {
        val entry = deps ?: return false
        val serviceScope = scope ?: return false
        if (recorder != null) return false
        if (entry.player().state.value.status != PlayerStatus.IDLE) {
            Toast.makeText(this, R.string.rec_busy, Toast.LENGTH_LONG).show()
            return false
        }
        floating?.setSuspended(true)
        val overlay = RecorderOverlay(this, serviceScope, entry, macroId) {
            recorder = null
            floating?.setSuspended(false)
        }
        if (!overlay.start()) {
            floating?.setSuspended(false)
            return false
        }
        recorder = overlay
        return true
    }

    /** Performs one action. Returns false when Android rejected or cancelled the gesture. */
    suspend fun perform(action: MacroAction): Boolean {
        val size = ScreenSize.of(this)
        fun px(v: Float, max: Int) = (v * max).coerceIn(0f, (max - 1).toFloat())
        val x = px(action.x, size.width)
        val y = px(action.y, size.height)
        return when (action.type) {
            ActionType.TAP -> gesture(x, y, null, null, TAP_MS)
            ActionType.LONG_PRESS -> gesture(x, y, null, null, maxOf(action.durationMs, LONG_PRESS_MIN_MS))
            ActionType.SWIPE -> gesture(
                x, y, px(action.endX, size.width), px(action.endY, size.height), action.durationMs,
            )
            ActionType.DELAY -> {
                delay(action.durationMs)
                true
            }
        }
    }

    /** Dispatches a single-stroke gesture in absolute pixels. A null end point means a press in place. */
    suspend fun gesture(x: Float, y: Float, endX: Float?, endY: Float?, durationMs: Long): Boolean =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine<Boolean> { cont ->
                val path = Path().apply {
                    moveTo(x, y)
                    if (endX != null && endY != null && (endX != x || endY != y)) lineTo(endX, endY)
                }
                val duration = durationMs.coerceIn(1L, GestureDescription.getMaxGestureDuration())
                val description = GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
                    .build()
                val accepted = dispatchGesture(
                    description,
                    object : GestureResultCallback() {
                        override fun onCompleted(gestureDescription: GestureDescription?) {
                            if (cont.isActive) cont.resume(true)
                        }

                        override fun onCancelled(gestureDescription: GestureDescription?) {
                            if (cont.isActive) cont.resume(false)
                        }
                    },
                    null,
                )
                if (!accepted && cont.isActive) cont.resume(false)
            }
        }

    companion object {
        private const val TAP_MS = 50L
        private const val LONG_PRESS_MIN_MS = 600L

        private val _instance = MutableStateFlow<MacroAccessibilityService?>(null)
        private val _connected = MutableStateFlow(false)

        /** The running service, or null when the user has not enabled it. */
        val instance: MacroAccessibilityService? get() = _instance.value

        /** True while the accessibility service is enabled and connected. */
        val connected: StateFlow<Boolean> = _connected.asStateFlow()

        internal fun publish(service: MacroAccessibilityService?) {
            _instance.value = service
            _connected.value = service != null
        }
    }
}
