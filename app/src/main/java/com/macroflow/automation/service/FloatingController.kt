package com.macroflow.automation.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.macroflow.automation.R
import com.macroflow.automation.domain.model.Macro
import com.macroflow.automation.engine.PlayerState
import com.macroflow.automation.engine.PlayerStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Draggable round button drawn over other apps (accessibility overlay window, no extra permission).
 * Tap it to run the selected macro, pause/resume, stop (emergency stop) or pick another macro.
 */
class FloatingController(
    private val service: MacroAccessibilityService,
    private val scope: CoroutineScope,
    private val deps: ServiceEntryPoint,
) {
    private val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val density = service.resources.displayMetrics.density

    private var root: LinearLayout? = null
    private var circle: TextView? = null
    private var circleBg: GradientDrawable? = null
    private var menu: LinearLayout? = null
    private var params: WindowManager.LayoutParams? = null

    private var menuOpen = false
    private var listMode = false
    private var suspended = false
    private var enabled = false
    private var state = PlayerState()
    private var selectedId: Long? = null
    private var macros: List<Macro> = emptyList()
    private val jobs = mutableListOf<Job>()

    fun start() {
        jobs += scope.launch { deps.settings().floatingEnabled.collect { enabled = it; sync() } }
        jobs += scope.launch { deps.settings().selectedMacroId.collect { selectedId = it; refreshMenu() } }
        jobs += scope.launch { deps.macros().observeMacros().collect { macros = it; refreshMenu() } }
        jobs += scope.launch { deps.player().state.collect { state = it; updateCircle(); refreshMenu() } }
    }

    fun stop() {
        jobs.forEach { it.cancel() }
        jobs.clear()
        removeView()
    }

    /** Hides the button while the recorder overlay is on screen. */
    fun setSuspended(value: Boolean) {
        suspended = value
        sync()
    }

    private fun dp(v: Int) = (v * density).toInt()

    private fun sync() {
        if (enabled && !suspended) {
            if (root == null) createView()
            updateCircle()
        } else {
            removeView()
        }
    }

    private fun createView() {
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(BLUE)
        }
        val circleView = TextView(service).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 18f
            background = bg
            layoutParams = LinearLayout.LayoutParams(dp(52), dp(52))
        }
        val menuView = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(MENU_BG)
            }
            setPadding(dp(6), dp(6), dp(6), dp(6))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(6) }
        }
        val rootView = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            addView(circleView)
            addView(menuView)
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(12)
            y = dp(180)
        }

        circleView.setOnTouchListener(object : View.OnTouchListener {
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
                            lp.y = startY + dy.toInt()
                            try {
                                wm.updateViewLayout(rootView, lp)
                            } catch (_: Exception) {
                            }
                        }
                    }
                    MotionEvent.ACTION_UP -> if (!moved) toggleMenu()
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
        circle = circleView
        circleBg = bg
        menu = menuView
        params = lp
        menuOpen = false
        listMode = false
    }

    private fun removeView() {
        root?.let {
            try {
                wm.removeView(it)
            } catch (_: Exception) {
            }
        }
        root = null
        circle = null
        circleBg = null
        menu = null
        params = null
        menuOpen = false
        listMode = false
    }

    private fun updateCircle() {
        val c = circle ?: return
        c.text = when (state.status) {
            PlayerStatus.IDLE -> "M"
            PlayerStatus.STARTING -> "…"
            PlayerStatus.PLAYING -> "▶"
            PlayerStatus.PAUSED -> "⏸"
        }
        circleBg?.setColor(
            when (state.status) {
                PlayerStatus.IDLE -> BLUE
                PlayerStatus.STARTING -> AMBER
                PlayerStatus.PLAYING -> GREEN
                PlayerStatus.PAUSED -> AMBER
            },
        )
    }

    private fun toggleMenu() {
        menuOpen = !menuOpen
        listMode = false
        renderMenu()
    }

    private fun closeMenu() {
        menuOpen = false
        listMode = false
        renderMenu()
    }

    private fun refreshMenu() {
        if (menuOpen) renderMenu()
    }

    private fun renderMenu() {
        val m = menu ?: return
        m.removeAllViews()
        if (!menuOpen) {
            m.visibility = View.GONE
            return
        }
        m.visibility = View.VISIBLE

        if (listMode) {
            m.addView(label(service.getString(R.string.float_select_title)))
            val items = macros.take(MAX_LIST_ITEMS)
            if (items.isEmpty()) m.addView(label(service.getString(R.string.float_none)))
            items.forEach { macro ->
                m.addView(
                    item(macro.name) {
                        scope.launch { deps.settings().setSelectedMacroId(macro.id) }
                        listMode = false
                        renderMenu()
                    },
                )
            }
            m.addView(item(service.getString(R.string.float_back)) {
                listMode = false
                renderMenu()
            })
            return
        }

        val player = deps.player()
        if (state.status == PlayerStatus.IDLE) {
            val selected = macros.firstOrNull { it.id == selectedId }
            val text = if (selected != null) {
                service.getString(R.string.float_run, selected.name)
            } else {
                service.getString(R.string.float_no_macro)
            }
            m.addView(item(text) {
                if (selected != null) {
                    closeMenu()
                    player.start(selected.id, 1, 0)
                }
            })
        } else {
            if (state.status == PlayerStatus.PAUSED) {
                m.addView(item(service.getString(R.string.float_resume)) { player.resume() })
            } else if (state.status == PlayerStatus.PLAYING) {
                m.addView(item(service.getString(R.string.float_pause)) { player.pause() })
            }
            m.addView(item(service.getString(R.string.float_stop)) {
                player.stop()
                closeMenu()
            })
        }
        m.addView(item(service.getString(R.string.float_select)) {
            listMode = true
            renderMenu()
        })
        m.addView(item(service.getString(R.string.float_hide)) {
            scope.launch { deps.settings().setFloatingEnabled(false) }
        })
    }

    private fun label(text: String) = TextView(service).apply {
        this.text = text
        setTextColor(0xFFA3AEC8.toInt())
        textSize = 12f
        setPadding(dp(10), dp(6), dp(10), dp(4))
    }

    private fun item(text: String, onClick: () -> Unit) = TextView(service).apply {
        this.text = text
        setTextColor(Color.WHITE)
        textSize = 14f
        maxWidth = dp(240)
        setPadding(dp(10), dp(10), dp(10), dp(10))
        setOnClickListener { onClick() }
    }

    private companion object {
        const val MAX_LIST_ITEMS = 8
        val BLUE = 0xFF398BFF.toInt()
        val GREEN = 0xFF2FBF85.toInt()
        val AMBER = 0xFFE8A13A.toInt()
        val MENU_BG = 0xF2161F33.toInt()
    }
}
