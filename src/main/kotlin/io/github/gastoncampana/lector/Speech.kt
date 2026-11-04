package io.github.gastoncampana.lector

import android.content.Context
import android.content.res.AssetManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** The Kokoro v1.0 voices Lector offers: each one's speaker id in the model's voice table, and its language. */
enum class Voice(val speaker: Int, val language: Language) {
    Dora(28, Language.SPANISH),
    Alex(29, Language.SPANISH),
    Santa(53, Language.SPANISH),
    Heart(3, Language.ENGLISH),
    Bella(2, Language.ENGLISH),
    Michael(16, Language.ENGLISH),
}

/** The espeak-ng voice that turns text in this language into Kokoro's phonemes. */
private val Language.espeak
    get() = when (this) {
        Language.SPANISH -> "es"
        Language.ENGLISH -> "en-us"
    }

private fun Context.settings() = getSharedPreferences("settings", Context.MODE_PRIVATE)

/** The voice picked for [language], or its first one. */
fun Context.voice(language: Language): Voice {
    val name = settings().getString(language.name, null)
    return Voice.entries.firstOrNull { it.name == name && it.language == language }
        ?: Voice.entries.first { it.language == language }
}

fun Context.setVoice(voice: Voice) = settings().edit().putString(voice.language.name, voice.name).apply()

/** How fast to speak: 1 is the voice's own pace. */
fun Context.speed(): Float = settings().getFloat("speed", 1f)

fun Context.setSpeed(speed: Float) = settings().edit().putFloat("speed", speed).apply()

/**
 * Speaks with Kokoro, a voice model in the app's assets, through sherpa-onnx. One thread generates the audio and
 * another plays it, so the next sentence is ready before the current one ends: the system TextToSpeech keeps only half
 * a second of audio ahead, too little for a model like this on a phone's CPU.
 */
object Speech {
    private val main = Handler(Looper.getMainLooper())
    private val generator = Executors.newSingleThreadExecutor()
    private val player = Executors.newSingleThreadExecutor()

    // Bumped (on the main thread) by every speak and stop: work for an older reading sees it changed and quits.
    @Volatile
    private var reading = 0

    // Loaded once, on the generator thread, which is the only one that touches it.
    private var tts: OfflineTts? = null

    private sealed interface Chunk
    private class Ready(val rate: Int) : Chunk
    private class Begin(val index: Int) : Chunk
    private class Audio(val samples: FloatArray) : Chunk
    private data object End : Chunk
    private data object Failed : Chunk

    /** Loads the model ahead of time: a couple of seconds the first reading would otherwise wait. */
    fun prepare(context: Context) {
        val app = context.applicationContext
        generator.execute { runCatching { model(app) } }
    }

    /**
     * Speaks each of [texts] with its voice, in order, at [speed], cutting off whatever was being spoken. [onStart] gets
     * each text's index as it begins to play; [onEnd] runs once after the last one, with true if the voice failed. Both
     * run on the main thread, and neither after [stop] or the next [speak].
     */
    fun speak(
        context: Context,
        texts: List<Pair<String, Voice>>,
        speed: Float,
        onStart: (Int) -> Unit,
        onEnd: (Boolean) -> Unit,
    ) {
        val id = ++reading
        val app = context.applicationContext
        // Bounded: on a fast phone a long text would otherwise pile up its audio in memory, sentence after sentence.
        val chunks = LinkedBlockingQueue<Chunk>(16)
        generator.execute {
            try {
                val tts = model(app)
                send(id, chunks, Ready(tts.sampleRate()))
                // An object, not a lambda: the native side calls invoke([F)Ljava/lang/Integer;, which a Kotlin lambda
                // doesn't have. Returning 0 makes it stop after the sentence it is on.
                val callback = object : (FloatArray) -> Int {
                    override fun invoke(samples: FloatArray) = if (send(id, chunks, Audio(samples.copyOf()))) 1 else 0
                }
                for ((i, text) in texts.withIndex()) {
                    if (!send(id, chunks, Begin(i))) return@execute
                    val (words, voice) = text
                    val config = GenerationConfig(
                        speed = speed,
                        sid = voice.speaker,
                        extra = mapOf("lang" to voice.language.espeak),
                    )
                    tts.generateWithConfigAndCallback(words, config, callback)
                }
                send(id, chunks, End)
            } catch (e: Exception) {
                send(id, chunks, Failed)
            }
        }
        player.execute { play(id, chunks, onStart, onEnd) }
    }

    /** Queues [chunk], waiting while the queue is full; false once the reading [id] is over and nobody plays it. */
    private fun send(id: Int, chunks: LinkedBlockingQueue<Chunk>, chunk: Chunk): Boolean {
        while (reading == id) if (chunks.offer(chunk, 100, TimeUnit.MILLISECONDS)) return true
        return false
    }

    fun stop() {
        reading++
    }

    private fun model(context: Context): OfflineTts {
        tts?.let { return it }
        val kokoro = OfflineTtsKokoroModelConfig(
            model = "kokoro/model.onnx",
            voices = "kokoro/voices.bin",
            tokens = "kokoro/tokens.txt",
            dataDir = espeakData(context).path,
            // The model needs a default; every generation names its own.
            lang = "es",
        )
        val config = OfflineTtsConfig(model = OfflineTtsModelConfig(kokoro = kokoro, numThreads = threads))
        return OfflineTts(context.assets, config).also { tts = it }
    }

    /**
     * espeak-ng's data, copied out of the assets: espeak-ng reads files, not assets. Copied again when the app is
     * updated, since the data may have changed with it.
     */
    private fun espeakData(context: Context): File {
        val dir = File(context.filesDir, "espeak-ng-data")
        val stamp = File(context.filesDir, "espeak-ng-data.version")
        val version = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime.toString()
        if (stamp.takeIf(File::exists)?.readText() == version) return dir
        copy(context.assets, "kokoro/espeak-ng-data", dir)
        stamp.writeText(version)
        return dir
    }

    private fun copy(assets: AssetManager, from: String, to: File) {
        val children = assets.list(from).orEmpty()
        if (children.isEmpty()) {
            to.parentFile?.mkdirs()
            assets.open(from).use { input -> to.outputStream().use(input::copyTo) }
        } else {
            children.forEach { copy(assets, "$from/$it", File(to, it)) }
        }
    }

    private fun play(id: Int, chunks: LinkedBlockingQueue<Chunk>, onStart: (Int) -> Unit, onEnd: (Boolean) -> Unit) {
        var track: AudioTrack? = null
        var rate = 0
        var written = 0L
        try {
            while (reading == id) {
                when (val chunk = chunks.poll(100, TimeUnit.MILLISECONDS) ?: continue) {
                    is Ready -> {
                        rate = chunk.rate
                        track = track(rate)
                        track.play()
                    }
                    is Begin -> post(id) { onStart(chunk.index) }
                    is Audio -> {
                        // A tenth of a second at a time, so a stop cuts in quickly.
                        val step = rate / 10
                        var at = 0
                        while (at < chunk.samples.size && reading == id) {
                            val n = minOf(step, chunk.samples.size - at)
                            val wrote = track!!.write(chunk.samples, at, n, AudioTrack.WRITE_BLOCKING)
                            check(wrote > 0) { "AudioTrack.write returned $wrote" }
                            at += wrote
                        }
                        written += at
                    }
                    End -> {
                        // Blocking writes keep the track at most a buffer ahead: let that play out.
                        val until = SystemClock.uptimeMillis() + 1000
                        while (reading == id && track!!.playbackHeadPosition < written &&
                            SystemClock.uptimeMillis() < until
                        ) Thread.sleep(20)
                        post(id) { onEnd(false) }
                        return
                    }
                    Failed -> {
                        post(id) { onEnd(true) }
                        return
                    }
                }
            }
        } catch (e: Exception) {
            // The audio output failed: no track, or the audio server went away mid-sentence.
            post(id) { onEnd(true) }
        } finally {
            track?.run {
                pause()
                flush()
                release()
            }
        }
    }

    private fun track(rate: Int) = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setSampleRate(rate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
        )
        .setTransferMode(AudioTrack.MODE_STREAM)
        .build()

    private fun post(id: Int, action: () -> Unit) {
        main.post { if (reading == id) action() }
    }

    /**
     * The phone's fast cores, at most 4: every step waits for its slowest thread, so a little core slows it down. A
     * Snapdragon 695 (2 fast, 6 little) generates faster on 2 threads than on 4.
     */
    private val threads: Int by lazy {
        val cpus = Runtime.getRuntime().availableProcessors()
        val speeds = (0 until cpus).map { cpu ->
            runCatching { File("/sys/devices/system/cpu/cpu$cpu/cpufreq/cpuinfo_max_freq").readText().trim().toLong() }
                .getOrDefault(0L)
        }
        val fast = speeds.count { it > speeds.min() }
        (if (fast == 0) cpus else fast).coerceIn(1, 4)
    }
}
