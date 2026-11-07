package com.macroflow.automation.service

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.macroflow.automation.R
import com.macroflow.automation.domain.model.Macro
import com.macroflow.automation.engine.PlayerState
import com.macroflow.automation.engine.PlayerStatus
import com.macroflow.automation.engine.ScreenSize
import com.macroflow.automation.ui.player.messageRes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min

/**
 * The floating control bar drawn over other apps (accessibility overlay window, no extra permission):
 * Record, Play, Stop, macro selector, Auto (play right after recording) and Loop (repeat until stopped).
 * The round "M" handle drags the bar; tapping it collapses or expands the bar.
 */
class FloatingController(
    private val service: MacroAccessibilityService,
    private val scope: CoroutineScope,
    private val deps: ServiceEntryPoint,
) {
    private val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val density = service.resources.displayMetrics.density

    private var root: LinearLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var panel: LinearLayout? = null
    private var handleBg: GradientDrawable? = null
    private var recordBtn: TextView? = null
    private var playBtn: TextView? = null
    private var stopBtn: TextView? = null
    private var selectorValue: TextView? = null
    private var autoSwitch: Switch? = null
    private var loopSwitch: Switch? = null
    private var dropdown: LinearLayout? = null
    private var panelColor = 0
    private var expandedWidth = 0

    private var enabled = false
    private var collapsed = false
    private var dropdownOpen = false
    private var playerState = PlayerState()
    private var recording: Int? = null
    private var macros: List<Macro> = emptyList()
    private var selectedId: Long? = null
    private var autoPlay = true
    private var loop = false
    private val jobs = mutableListOf<Job>()

    private val autoListener = CompoundButton.OnCheckedChangeListener { _, checked ->
        scope.launch { deps.settings().setAutoPlayAfterRecord(checked) }
    }
    private val loopListener = CompoundButton.OnCheckedChangeListener { _, checked ->
        scope.launch { deps.settings().setLoopEnabled(checked) }
    }

    fun start() {
        jobs += scope.launch { deps.settings().floatingEnabled.collect { enabled = it; sync() } }
        jobs += scope.launch { deps.settings().selectedMacroId.collect { selectedId = it; refresh() } }
        jobs += scope.launch { deps.settings().autoPlayAfterRecord.collect { autoPlay = it; refresh() } }
        jobs += scope.launch { deps.settings().loopEnabled.collect { loop = it; refresh() } }
        jobs += scope.launch { deps.macros().observeMacros().collect { macros = it; refresh() } }
        jobs += scope.launch { service.recordingCount.collect { recording = it; refresh() } }
        jobs += scope.launch {
            deps.player().state.collect { state ->
                playerState = state
                refresh()
                // The app's own snackbar is not visible while another app is in front, so report here too.
                val error = state.error
                if (error != null && root != null) {
                    Toast.makeText(service, error.messageRes(), Toast.LENGTH_LONG).show()
                    deps.player().clearError()
                }
            }
        }
    }

    fun stop() {
        jobs.forEach { it.cancel() }
        jobs.clear()
        removeView()
    }

    /** Re-adds the bar so it sits above windows added after it (the recorder's capture layer). */
    fun bringToFront() {
        val r = root ?: return
        val lp = params ?: return
        try {
            wm.removeView(r)
            wm.addView(r, lp)
        } catch (_: Exception) {
        }
    }

    private fun dp(v: Int) = (v * density).toInt()

    private fun sync() {
        if (enabled) {
            if (root == null) createView()
            refresh()
        } else {
            removeView()
        }
    }

    // ---------------------------------------------------------------- views

    private fun createView() {
        val screenWidth = ScreenSize.of(service).width
        val narrow = screenWidth / density < NARROW_DP
        expandedWidth = min(screenWidth - dp(16), dp(if (narrow) 520 else 760))

        val handleDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(BLUE)
        }
        val handle = TextView(service).apply {
            text = "M"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            background = handleDrawable
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply {
                gravity = Gravity.CENTER_VERTICAL
                marginEnd = dp(6)
            }
        }

        val record = actionButton { onRecordClicked() }
        val play = actionButton { onPlayClicked() }
        val stop = actionButton { onStopClicked() }

        val selectorTitle = TextView(service).apply {
            text = service.getString(R.string.bar_select)
            setTextColor(MUTED)
            textSize = 10f
        }
        val selectorText = TextView(service).apply {
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = rounded(FIELD_BG, GRAY_STROKE, 10)
        }
        val selector = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f).apply {
                marginStart = dp(4)
                marginEnd = dp(4)
            }
            addView(selectorTitle)
            addView(selectorText)
            setOnClickListener {
                dropdownOpen = !dropdownOpen
                refresh()
            }
        }

        val auto = newSwitch()
        val loopSw = newSwitch()

        val row1 = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(record)
            addView(play)
            addView(stop)
        }
        val row2 = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(selector)
            addView(toggleBox(service.getString(R.string.bar_auto), "⚡", auto))
            addView(toggleBox(service.getString(R.string.bar_loop), "↻", loopSw))
        }
        val content = LinearLayout(service).apply {
            orientation = if (narrow) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            if (narrow) {
                addView(row1, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
                addView(row2, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
            } else {
                addView(row1, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(row2, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.6f))
            }
        }
        val dropdownView = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            background = rounded(FIELD_BG, GRAY_STROKE, 12)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(6) }
        }
        val panelView = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(content)
            addView(dropdownView)
        }
        val rootView = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(handle)
            addView(panelView)
        }

        val lp = WindowManager.LayoutParams(
            expandedWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(8)
            y = dp(160)
        }

        handle.setOnTouchListener(object : View.OnTouchListener {
            private var downX = 0f
            private var downY = 0f
            private var startX = 0
            private var startY = 0
            private var moved = false

            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = e.rawX
                        downY = e.rawY
                        startX = lp.x
                        startY = lp.y
                        moved = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = e.rawX - downX
                        val dy = e.rawY - downY
                        if (abs(dx) > dp(6) || abs(dy) > dp(6)) moved = true
                        if (moved) {
                            lp.x = startX + dx.toInt()
                            lp.y = (startY + dy.toInt()).coerceAtLeast(0)
                            try {
                                wm.updateViewLayout(rootView, lp)
                            } catch (_: Exception) {
                            }
                        }
                    }
                    MotionEvent.ACTION_UP -> if (!moved) toggleCollapsed()
                }
                return true
            }
        })

        try {
            wm.addView(rootView, lp)
        } catch (e: Exception) {
            return
        }
        root = rootView
        params = lp
        panel = panelView
        handleBg = handleDrawable
        recordBtn = record
        playBtn = play
        stopBtn = stop
        selectorValue = selectorText
        autoSwitch = auto
        loopSwitch = loopSw
        dropdown = dropdownView
        panelColor = 0
        collapsed = false
        dropdownOpen = false
    }

    private fun removeView() {
        root?.let {
            try {
                wm.removeView(it)
            } catch (_: Exception) {
            }
        }
        root = null
        params = null
        panel = null
        handleBg = null
        recordBtn = null
        playBtn = null
        stopBtn = null
        selectorValue = null
        autoSwitch = null
        loopSwitch = null
        dropdown = null
        dropdownOpen = false
    }

    private fun toggleCollapsed() {
        collapsed = !collapsed
        dropdownOpen = false
        val r = root ?: return
        val lp = params ?: return
        panel?.visibility = if (collapsed) View.GONE else View.VISIBLE
        lp.width = if (collapsed) WindowManager.LayoutParams.WRAP_CONTENT else expandedWidth
        try {
            wm.updateViewLayout(r, lp)
        } catch (_: Exception) {
        }
        refresh()
    }

    // ---------------------------------------------------------------- state

    private fun refresh() {
        if (root == null) return
        val rec = recording
        val playing = playerState.status != PlayerStatus.IDLE
        val color = when {
            rec != null -> RED
            playerState.status == PlayerStatus.PLAYING -> GREEN
            playerState.status != PlayerStatus.IDLE -> AMBER
            else -> BLUE
        }
        if (color != panelColor) {
            panelColor = color
            panel?.background = neon(color)
            handleBg?.setColor(color)
        }

        recordBtn?.apply {
            text = if (rec != null) service.getString(R.string.bar_recording, rec) else "● " + service.getString(R.string.bar_record)
            background = rounded(if (rec != null) RED_FILL else BTN_BG, RED, 12)
            alpha = if (playing) 0.4f else 1f
        }
        playBtn?.apply {
            text = "▶ " + service.getString(R.string.bar_play)
            background = rounded(BTN_BG, GREEN, 12)
            alpha = if (playing || rec != null) 0.4f else 1f
        }
        stopBtn?.apply {
            text = "■ " + service.getString(R.string.bar_stop)
            background = rounded(BTN_BG, GRAY_LIGHT, 12)
            alpha = if (playing || rec != null) 1f else 0.4f
        }
        val selected = macros.firstOrNull { it.id == selectedId }
        selectorValue?.text = (selected?.name ?: service.getString(R.string.bar_none)) + "  ▼"
        setChecked(autoSwitch, autoPlay, autoListener)
        setChecked(loopSwitch, loop, loopListener)
        renderDropdown()
    }

    private fun setChecked(sw: Switch?, value: Boolean, listener: CompoundButton.OnCheckedChangeListener) {
        if (sw == null || sw.isChecked == value) return
        sw.setOnCheckedChangeListener(null)
        sw.isChecked = value
        sw.setOnCheckedChangeListener(listener)
    }

    private fun renderDropdown() {
        val d = dropdown ?: return
        d.removeAllViews()
        if (!dropdownOpen || collapsed) {
            d.visibility = View.GONE
            return
        }
        d.visibility = View.VISIBLE
        macros.take(MAX_LIST_ITEMS).forEach { macro ->
            val mark = if (macro.id == selectedId) "✓ " else ""
            d.addView(row(mark + macro.name) {
                dropdownOpen = false
                scope.launch { deps.settings().setSelectedMacroId(macro.id) }
                refresh()
            })
        }
        d.addView(row(service.getString(R.string.bar_new_macro)) {
            dropdownOpen = false
            createMacro()
            refresh()
        })
    }

    private fun createMacro() {
        scope.launch {
            try {
                val id = deps.macros().create("Macro ${macros.size + 1}", "")
                deps.settings().setSelectedMacroId(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(service, R.string.error_generic, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ---------------------------------------------------------------- actions

    private fun onRecordClicked() {
        if (recording != null) return
        if (playerState.status != PlayerStatus.IDLE) {
            Toast.makeText(service, R.string.rec_busy, Toast.LENGTH_SHORT).show()
            return
        }
        val id = selectedId
        if (id == null) {
            Toast.makeText(service, R.string.bar_pick_first, Toast.LENGTH_SHORT).show()
            return
        }
        dropdownOpen = false
        service.startRecording(id)
    }

    private fun onPlayClicked() {
        if (recording != null || playerState.status != PlayerStatus.IDLE) return
        val id = selectedId
        if (id == null) {
            Toast.makeText(service, R.string.bar_pick_first, Toast.LENGTH_SHORT).show()
            return
        }
        dropdownOpen = false
        deps.player().start(id, 1, 0, loop)
    }

    /** Stops the recording (and saves it) or stops the running macro. */
    private fun onStopClicked() {
        when {
            recording != null -> service.stopRecording(save = true)
            playerState.status != PlayerStatus.IDLE -> deps.player().stop()
        }
    }

    // ---------------------------------------------------------------- view helpers

    private fun actionButton(onClick: () -> Unit) = TextView(service).apply {
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
        maxLines = 1
        layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f).apply {
            marginStart = dp(3)
            marginEnd = dp(3)
        }
        setOnClickListener { onClick() }
    }

    private fun toggleBox(label: String, icon: String, sw: Switch) = LinearLayout(service).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        addView(TextView(service).apply {
            text = "$icon $label"
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
        })
        addView(sw)
    }

    private fun newSwitch(): Switch = Switch(service).apply {
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        thumbTintList = ColorStateList(states, intArrayOf(Color.WHITE, 0xFF9AA4BA.toInt()))
        trackTintList = ColorStateList(states, intArrayOf(BLUE, 0xFF3A4254.toInt()))
    }

    private fun row(text: String, onClick: () -> Unit) = TextView(service).apply {
        this.text = text
        setTextColor(Color.WHITE)
        textSize = 14f
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        setPadding(dp(12), dp(10), dp(12), dp(10))
        setOnClickListener { onClick() }
    }

    private fun rounded(fill: Int, stroke: Int, radiusDp: Int) = GradientDrawable().apply {
        cornerRadius = dp(radiusDp).toFloat()
        setColor(fill)
        setStroke(dp(1) + 1, stroke)
    }

    /** Dark panel with a soft glow in [color]: two translucent outer rings around a bordered body. */
    private fun neon(color: Int): Drawable {
        val outer = GradientDrawable().apply {
            cornerRadius = dp(22).toFloat()
            setColor(Color.TRANSPARENT)
            setStroke(dp(5), withAlpha(color, 0x26))
        }
        val mid = GradientDrawable().apply {
            cornerRadius = dp(19).toFloat()
            setColor(Color.TRANSPARENT)
            setStroke(dp(3), withAlpha(color, 0x66))
        }
        val body = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(BODY_BG)
            setStroke(dp(1) + 1, color)
        }
        return LayerDrawable(arrayOf(outer, mid, body)).apply {
            setLayerInset(1, dp(2), dp(2), dp(2), dp(2))
            setLayerInset(2, dp(4), dp(4), dp(4), dp(4))
        }
    }

    private fun withAlpha(color: Int, alpha: Int) = (color and 0x00FFFFFF) or (alpha shl 24)

    private companion object {
        const val NARROW_DP = 600f
        const val MAX_LIST_ITEMS = 8
        val BLUE = 0xFF398BFF.toInt()
        val GREEN = 0xFF2FBF85.toInt()
        val AMBER = 0xFFE8A13A.toInt()
        val RED = 0xFFFF3B4D.toInt()
        val RED_FILL = 0xFF7A1420.toInt()
        val BODY_BG = 0xF2070B14.toInt()
        val BTN_BG = 0xFF101827.toInt()
        val FIELD_BG = 0xFF0C1220.toInt()
        val GRAY_STROKE = 0xFF3A4254.toInt()
        val GRAY_LIGHT = 0xFFB9C2D6.toInt()
        val MUTED = 0xFFA3AEC8.toInt()
    }
}
