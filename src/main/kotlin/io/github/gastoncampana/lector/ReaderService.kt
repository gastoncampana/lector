package io.github.gastoncampana.lector

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView

/**
 * Reads the screen aloud. Its accessibility shortcut (both volume keys, the button or the gesture) collects the text
 * of the app in front and covers it with a [ReaderView]: a tap on a block reads from there to the end, filling the
 * block being read. Any other tap, a tap while reading, or the shortcut again stops and closes.
 * Speech goes through the phone's default TextToSpeech engine, so its voice, speed and pitch are the system's.
 */
class ReaderService : AccessibilityService() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var tts: TextToSpeech

    // Null while the engine is still binding, false if it failed.
    private var ready: Boolean? = null
    private var overlay: ReaderView? = null

    // The id of a reading's final utterance: its onDone closes the overlay. Written before the first speak.
    @Volatile
    private var last = ""

    // Called on the engine's binder thread.
    private val progress = object : UtteranceProgressListener() {
        override fun onStart(id: String) {
            main.post { overlay?.reading = id.substringBefore('/').toInt() }
        }

        override fun onDone(id: String) {
            if (id == last) main.post(::close)
        }

        @Deprecated("Superseded by onError(String, Int), whose default calls this one")
        override fun onError(id: String) {
            main.post(::close)
        }
    }

    override fun onCreate() {
        super.onCreate()
        tts = voice()
    }

    /** A TextToSpeech on the default engine, as set when it binds. */
    private fun voice(): TextToSpeech {
        ready = null
        return TextToSpeech(this) { ready = it == TextToSpeech.SUCCESS }.apply {
            setOnUtteranceProgressListener(progress)
        }
    }

    /** The engine failed to bind (early in boot) or dropped (an engine update): starts over for the next press. */
    private fun restart() {
        close()
        tts.shutdown()
        tts = voice()
        notice(R.string.no_voice)
    }

    override fun onServiceConnected() {
        accessibilityButtonController.registerAccessibilityButtonCallback(
            object : AccessibilityButtonController.AccessibilityButtonCallback() {
                override fun onClicked(controller: AccessibilityButtonController) = toggle()
            },
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = close()

    override fun onDestroy() {
        close()
        tts.shutdown()
        super.onDestroy()
    }

    private fun toggle(again: Boolean = false) {
        if (overlay != null) return close()
        when (ready) {
            null -> return notice(R.string.no_voice)
            false -> return restart()
            true -> Unit
        }
        val root = rootInActiveWindow
        val blocks = mutableListOf<Block>().also { collect(root, it) }
        // Picked in the chooser Android shows when several services share the shortcut, this runs while the chooser
        // (an "android" window) is still the active one: look again once it has closed.
        if (!again && (blocks.isEmpty() || root?.packageName == "android")) {
            main.postDelayed({ toggle(again = true) }, 500)
            return
        }
        if (blocks.isEmpty()) return notice(R.string.no_text)
        val view = ReaderView(this, blocks, onPick = { read(blocks, it) }, onClose = ::close)
        getSystemService(WindowManager::class.java).addView(view, overlayLayout())
        overlay = view
    }

    /** Queues [blocks] from [from] to the end, each cut to the engine's input limit. Utterance ids are "block/piece". */
    private fun read(blocks: List<Block>, from: Int) {
        val max = TextToSpeech.getMaxSpeechInputLength()
        val queue = (from until blocks.size).flatMap { i ->
            pieces(blocks[i].text, max).mapIndexed { k, piece -> "$i/$k" to piece }
        }
        last = queue.last().first
        val failed = queue.withIndex().any { (n, utterance) ->
            val mode = if (n == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            tts.speak(utterance.second, mode, null, utterance.first) == TextToSpeech.ERROR
        }
        if (failed) return restart()
        overlay?.reading = from
    }

    private fun close() {
        tts.stop()
        // An onStart or onDone already posted would otherwise reach the next reading's overlay.
        main.removeCallbacksAndMessages(null)
        last = ""
        overlay?.let { getSystemService(WindowManager::class.java).removeView(it) }
        overlay = null
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
        view.postDelayed({ if (view.isAttachedToWindow) windows.removeView(view) }, 2500)
    }
}

/**
 * Adds the visible text under [node] to [into], in the app's own order. A node with text stands for its whole subtree.
 * ponytail: text only, no contentDescription, so images and icon-only buttons stay silent; add it if they are missed.
 */
private fun collect(node: AccessibilityNodeInfo?, into: MutableList<Block>) {
    if (node == null || !node.isVisibleToUser) return
    val text = if (node.isPassword) "" else node.text?.toString()?.trim().orEmpty()
    if (text.isNotEmpty()) {
        val bounds = Rect().also(node::getBoundsInScreen)
        into += Block(text, bounds.left, bounds.top, bounds.right, bounds.bottom)
        return
    }
    for (i in 0 until node.childCount) collect(node.getChild(i), into)
}

/** Full screen, over the cutout and the system bars, so the view's coordinates line up with the screen's. */
private fun overlayLayout() = LayoutParams(
    LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
    LayoutParams.FLAG_NOT_FOCUSABLE or LayoutParams.FLAG_LAYOUT_IN_SCREEN,
    PixelFormat.TRANSLUCENT,
).apply {
    layoutInDisplayCutoutMode = LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
    fitInsetsTypes = 0
}

/** Dims the screen and outlines every block until one is picked; then fills only the one being read. */
@SuppressLint("ViewConstructor")
private class ReaderView(
    context: Context,
    private val blocks: List<Block>,
    private val onPick: (Int) -> Unit,
    private val onClose: () -> Unit,
) : View(context) {
    var reading = -1
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

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(0x33000000)
        // Blocks are in screen coordinates; the window may still start below the top (e.g. a cutout the system keeps).
        getLocationOnScreen(origin)
        canvas.translate(-origin[0].toFloat(), -origin[1].toFloat())
        val corner = 4 * dp
        blocks.forEachIndexed { i, b ->
            val paint = if (reading < 0) outline else if (i == reading) fill else return@forEachIndexed
            canvas.drawRoundRect(
                b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(), corner, corner, paint,
            )
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            performClick()
            val picked = if (reading < 0) blockAt(blocks, event.rawX.toInt(), event.rawY.toInt()) else -1
            if (picked < 0) onClose() else onPick(picked)
        }
        return true
    }

    override fun performClick() = super.performClick()
}

/**
 * The app's icon: opens Accessibility settings, where the service is turned on and given a shortcut. Its own page
 * (ACCESSIBILITY_DETAILS_SETTINGS) is not public API.
 */
class SetupActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        finish()
    }
}
