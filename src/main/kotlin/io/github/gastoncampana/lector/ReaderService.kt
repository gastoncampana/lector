package io.github.gastoncampana.lector

import android.accessibilityservice.AccessibilityService
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import java.util.Locale
import kotlin.math.abs

/**
 * Reads the screen aloud. Its accessibility shortcut (both volume keys, the button or the gesture) turns the service
 * on, which covers the app in front with a [ReaderView] and outlines its text: a tap on a block reads from there to the
 * end, filling the blocks being read. Any other tap, a tap while reading, or the end of the reading closes it and turns
 * the service off again; the shortcut pressed meanwhile turns it off too. So it runs only while it reads, and apps that
 * refuse to run beside an accessibility service, as banking apps do, find none the rest of the time. It speaks with
 * [Speech], in the voice picked in [SettingsActivity] for each run's language.
 */
class ReaderService : AccessibilityService() {
    private val main = Handler(Looper.getMainLooper())
    private var overlay: ReaderView? = null

    override fun onServiceConnected() {
        Speech.prepare(this)
        open()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = finish()

    override fun onDestroy() {
        close()
        super.onDestroy()
    }

    /** Covers the screen at once, so the shortcut is seen to work, and [look]s for the text to outline. */
    private fun open() {
        val view = ReaderView(this, emptyList(), onPick = ::read, onClose = { finish() })
        getSystemService(WindowManager::class.java).addView(view, overlayLayout())
        overlay = view
        look(view)
    }

    private fun look(view: ReaderView, tries: Int = 0) {
        val root = rootInActiveWindow
        val blocks = readable(mutableListOf<Block>().also { collect(root, it) })
        // Just turned on, the service may not see the window yet, and apps that build their tree only while a service
        // is on (Chrome) need a moment; picked in the chooser Android shows when several services share the shortcut,
        // it starts while the chooser (an "android" window) is the active one. Keep looking for up to 3 seconds.
        if (tries < OPEN_TRIES && (blocks.isEmpty() || root?.packageName == "android")) {
            main.postDelayed({ look(view, tries + 1) }, OPEN_MS)
            return
        }
        if (blocks.isEmpty()) return finish(R.string.no_text)
        view.blocks = blocks
        // Chrome stops building a page's tree while nothing reads it and starts again when asked, so the first look
        // may find only its toolbar: look again shortly, unless a block was already picked.
        main.postDelayed({
            val more = readable(mutableListOf<Block>().also { collect(rootInActiveWindow, it) })
            if (overlay === view && view.reading.isEmpty() && more.size > view.blocks.size) view.blocks = more
        }, 500)
    }

    /**
     * Reads [blocks] from [from] to the end, a [runs] at a time, each with the voice picked for its language, filling
     * each run's blocks as it starts.
     */
    private fun read(blocks: List<Block>, from: Int) {
        val runs = runs(blocks, from)
        // A run with nothing that tells its language (a name, a number) keeps the one before it.
        var language = if (Locale.getDefault().language == "en") Language.ENGLISH else Language.SPANISH
        val texts = runs.map { run ->
            val text = run.joinToString(" ") { blocks[it].text }
            language = language(text) ?: language
            text to voice(language)
        }
        overlay?.reading = runs.first()
        Speech.speak(
            this,
            texts,
            speed(),
            onStart = { overlay?.reading = runs[it] },
            onEnd = { failed -> finish(if (failed) R.string.voice_failed else null) },
        )
    }

    private fun close() {
        Speech.stop()
        main.removeCallbacksAndMessages(null)
        overlay?.let { getSystemService(WindowManager::class.java).removeView(it) }
        overlay = null
    }

    /** Closes and turns the service off, after showing [message] if there is one: its window goes with the service. */
    private fun finish(message: Int? = null) {
        close()
        if (message == null) return disableSelf()
        notice(message)
        main.postDelayed(::disableSelf, NOTICE_MS)
    }

    /**
     * A short message near the bottom of the screen. Not a Toast: Android hides a background app's toasts while it may
     * not post notifications, and this app asks for no permissions.
     */
    private fun notice(text: Int) {
        val dp = resources.displayMetrics.density
        val view = TextView(this).apply {
            setText(text)
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding((16 * dp).toInt(), (10 * dp).toInt(), (16 * dp).toInt(), (10 * dp).toInt())
            background = GradientDrawable().apply {
                setColor(0xE6303030.toInt())
                cornerRadius = 24 * dp
            }
        }
        val windows = getSystemService(WindowManager::class.java)
        windows.addView(
            view,
            LayoutParams(
                LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                LayoutParams.FLAG_NOT_FOCUSABLE or LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT,
            ).apply {
                width = LayoutParams.WRAP_CONTENT
                height = LayoutParams.WRAP_CONTENT
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                y = (96 * dp).toInt()
            },
        )
        view.postDelayed({ if (view.isAttachedToWindow) windows.removeView(view) }, NOTICE_MS)
    }
}

private const val NOTICE_MS = 2500L
private const val OPEN_MS = 250L
private const val OPEN_TRIES = 12
private const val PULSE_MS = 1000L

/** Where a node sits, as far as reading goes: what holds it decides whether its text is read. */
private enum class Place { TEXT, HEADING, LINK, SUPERSCRIPT, CONTROL }

// Native controls; their text is a label, not something to read.
private val controls = setOf(
    "android.widget.Button", "android.widget.ImageButton", "android.widget.CompoundButton", "android.widget.CheckBox",
    "android.widget.RadioButton", "android.widget.Switch", "android.widget.ToggleButton",
)

/**
 * Adds the visible text under [node] to [into], in the app's own order. A node with text stands for its whole subtree.
 * Controls are left out: buttons, text fields (a browser's address bar), and in a web page (Chrome names each node's
 * role) footnote marks too, links in a superscript; the rest of a superscript is read, as the 2 of km². Text inside a
 * link is marked as such, unless a heading holds the link, as a news site's headlines.
 * ponytail: text only, no contentDescription, so images and icon-only buttons stay silent; add it if they are missed.
 */
private fun collect(
    node: AccessibilityNodeInfo?,
    into: MutableList<Block>,
    parent: Int = 0,
    grandparent: Int = 0,
    context: Place = Place.TEXT,
) {
    if (node == null || !node.isVisibleToUser) return
    val role = node.extras.getString("AccessibilityNodeInfo.chromeRole")
    val here = when {
        context == Place.CONTROL -> context
        node.className in controls || node.isEditable || role == "button" -> Place.CONTROL
        role == "link" && context == Place.SUPERSCRIPT -> Place.CONTROL
        role == "superscript" -> Place.SUPERSCRIPT
        role == "heading" -> Place.HEADING
        role == "link" && context != Place.HEADING -> Place.LINK
        else -> context
    }
    if (here == Place.CONTROL) return
    val text = if (node.isPassword) "" else node.text?.toString()?.trim().orEmpty()
    // Also footnote matter: the page after a mark (Wikipedia's ":12") and the brackets around a "citation needed".
    if (text.isNotEmpty()) {
        if (here == Place.SUPERSCRIPT && (text.startsWith(":") || text.none(Char::isLetterOrDigit))) return
        val bounds = Rect().also(node::getBoundsInScreen)
        into += Block(
            text, bounds.left, bounds.top, bounds.right, bounds.bottom, parent, grandparent, here == Place.LINK,
        )
        return
    }
    // A node's hash is its id in the window: unique for as long as the walk takes.
    for (i in 0 until node.childCount) collect(node.getChild(i), into, node.hashCode(), parent, here)
}

/**
 * Full screen, over the cutout and the system bars, so the view's coordinates line up with the screen's. It keeps the
 * screen on: an accessibility service counts as in the foreground only while the screen is, so once it goes off
 * Android mutes the app's audio and leaves it the slow cores.
 */
private fun overlayLayout() = LayoutParams(
    LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
    LayoutParams.FLAG_NOT_FOCUSABLE or LayoutParams.FLAG_LAYOUT_IN_SCREEN or LayoutParams.FLAG_KEEP_SCREEN_ON,
    PixelFormat.TRANSLUCENT,
).apply {
    layoutInDisplayCutoutMode = LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
    fitInsetsTypes = 0
}

/** Dims the screen and outlines every block until one is picked; then fills only the ones being read. */
@SuppressLint("ViewConstructor")
private class ReaderView(
    context: Context,
    blocks: List<Block>,
    private val onPick: (List<Block>, Int) -> Unit,
    private val onClose: () -> Unit,
) : View(context) {
    var blocks = blocks
        set(value) {
            field = value
            invalidate()
        }

    /** The blocks being read; none until one is picked. */
    var reading = IntRange.EMPTY
        set(value) {
            field = value
            invalidate()
        }

    private val origin = IntArray(2)
    private val dp = resources.displayMetrics.density
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2 * dp
        color = 0xFF4A90E2.toInt()
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66FFC400 }
    private val edge = Paint(outline).apply { strokeWidth = 8 * dp }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(0x33000000)
        if (blocks.isEmpty()) {
            // Still looking for the text: the screen's edge pulses until the outlines show, or stays lit without
            // animations.
            val animate = ValueAnimator.areAnimatorsEnabled()
            val phase = SystemClock.uptimeMillis() % PULSE_MS / PULSE_MS.toFloat()
            edge.alpha = if (animate) (255 * (0.25f + 0.75f * abs(1 - 2 * phase))).toInt() else 255
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), edge)
            if (animate) postInvalidateOnAnimation()
            return
        }
        // Blocks are in screen coordinates; the window may still start below the top (e.g. a cutout the system keeps).
        getLocationOnScreen(origin)
        canvas.translate(-origin[0].toFloat(), -origin[1].toFloat())
        val corner = 4 * dp
        blocks.forEachIndexed { i, b ->
            val paint = if (reading.isEmpty()) outline else if (i in reading) fill else return@forEachIndexed
            canvas.drawRoundRect(
                b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(), corner, corner, paint,
            )
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            performClick()
            val picked = if (reading.isEmpty()) blockAt(blocks, event.rawX.toInt(), event.rawY.toInt()) else -1
            if (picked < 0) onClose() else onPick(blocks, picked)
        }
        return true
    }

    override fun performClick() = super.performClick()
}
