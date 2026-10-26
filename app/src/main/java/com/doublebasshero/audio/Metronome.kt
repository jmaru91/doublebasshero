package com.doublebasshero.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process
import com.doublebasshero.engine.BarKind
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Sample-accurate metronome on one continuous AudioTrack stream.
 *
 * Plays quarter notes only. Beat 1 is accented: high = the bar is 16ths, lower =
 * the bar is 8ths or a rest bar. Count-in bars, chimes and the end-of-rest cue are
 * mixed into the same stream, and silence keeps flowing during rests so timing
 * never drifts and the service stays alive with the screen off.
 */
class Metronome {
    private val sampleRate = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC).takeIf { it > 0 } ?: 48000
    private val chunk = 256

    private val accentHigh = tone(1600.0, 0.07, 1.0)
    private val accentLow = tone(1250.0, 0.07, 1.0)
    private val beat = tone(1000.0, 0.06, 0.8)
    private val countIn = tone(800.0, 0.06, 0.75)
    private val chime = concat(tone(880.0, 0.12, 0.6), tone(1320.0, 0.18, 0.6))
    private val restCue = concat(tone(660.0, 0.12, 0.7), tone(990.0, 0.2, 0.7))

    /** Frames written so far; the beat grid is expressed in these frames. */
    @Volatile private var writtenFrames = 0L

    private class Grid(val startFrame: Long, val framesPerBeat: Double, val pattern: List<BarKind>, val countInBeats: Int)

    @Volatile private var grid: Grid? = null
    @Volatile var muted = false
    @Volatile private var running = false
    private var thread: Thread? = null
    private var track: AudioTrack? = null

    private class Voice(val samples: FloatArray, var pos: Int)
    private val pendingSounds = ArrayList<FloatArray>()
    private val voices = ArrayList<Voice>()

    fun start() {
        if (running) return
        val minBuf = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(maxOf(minBuf, chunk * 2 * 4))
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
        track = t
        writtenFrames = 0
        running = true
        t.play()
        thread = Thread({ loop(t) }, "metronome").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun stop() {
        running = false
        thread?.join(500)
        thread = null
        track?.run { runCatching { stop() }; release() }
        track = null
        grid = null
    }

    /**
     * Starts clicking at [bpm] with [pattern] a few milliseconds from now, after
     * [countInBeats] count-in beats. Returns the delay in ms until the pattern's
     * first beat, so the caller can start its own step timer in sync.
     */
    fun play(bpm: Int, pattern: List<BarKind>, countInBeats: Int = 0): Long {
        val framesPerBeat = sampleRate * 60.0 / bpm
        val lead = (sampleRate * 0.12).toLong()
        val start = writtenFrames + lead
        grid = Grid(start, framesPerBeat, pattern.ifEmpty { listOf(BarKind.SIXTEENTHS) }, countInBeats)
        return ((lead + countInBeats * framesPerBeat) * 1000 / sampleRate).toLong()
    }

    /** Stops clicking; silence keeps streaming. */
    fun silence() {
        grid = null
    }

    fun chime() = enqueue(chime)
    fun restCue() = enqueue(restCue)

    private fun enqueue(s: FloatArray) {
        if (muted) return
        synchronized(pendingSounds) { pendingSounds += s }
    }

    /**
     * Where playback is in the current pattern: (beat in bar 0..3, bar index), or
     * null while counting in or idle. Uses the playback head so it matches what is heard.
     */
    fun position(): Pair<Int, Int>? {
        val g = grid ?: return null
        val t = track ?: return null
        val played = (t.playbackHeadPosition.toLong() and 0xffffffffL)
        val beats = (played - g.startFrame) / g.framesPerBeat - g.countInBeats
        if (beats < 0) return null
        val b = beats.toLong()
        return (b % 4).toInt() to (b / 4).toInt()
    }

    /** 8th notes played since the pattern started (count-in excluded), or null: two per click. */
    fun eighthPosition(): Long? {
        val g = grid ?: return null
        val t = track ?: return null
        val played = (t.playbackHeadPosition.toLong() and 0xffffffffL)
        val beats = (played - g.startFrame) / g.framesPerBeat - g.countInBeats
        return if (beats < 0) null else (beats * 2).toLong()
    }

    fun isCountingIn(): Boolean {
        val g = grid ?: return false
        val t = track ?: return false
        val played = (t.playbackHeadPosition.toLong() and 0xffffffffL)
        return (played - g.startFrame) / g.framesPerBeat < g.countInBeats
    }

    private fun loop(t: AudioTrack) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val mix = FloatArray(chunk)
        val out = ShortArray(chunk)
        while (running) {
            java.util.Arrays.fill(mix, 0f)
            val base = writtenFrames
            synchronized(pendingSounds) {
                pendingSounds.forEach { voices += Voice(it, 0) }
                pendingSounds.clear()
            }
            val g = grid
            if (g != null && !muted) {
                // Beats whose start falls inside this chunk.
                var k = kotlin.math.ceil((base - g.startFrame) / g.framesPerBeat).toLong().coerceAtLeast(0)
                while (true) {
                    val frame = g.startFrame + (k * g.framesPerBeat).toLong()
                    if (frame >= base + chunk) break
                    if (frame >= base) voices += Voice(clickFor(g, k), -(frame - base).toInt())
                    k++
                }
            }
            val it = voices.iterator()
            while (it.hasNext()) {
                val v = it.next()
                for (i in 0 until chunk) {
                    val p = v.pos + i
                    if (p >= 0 && p < v.samples.size) mix[i] += v.samples[p]
                }
                v.pos += chunk
                if (v.pos >= v.samples.size) it.remove()
            }
            for (i in 0 until chunk) out[i] = (limit(mix[i]) * Short.MAX_VALUE).toInt().toShort()
            t.write(out, 0, chunk)
            writtenFrames = base + chunk
        }
    }

    private fun clickFor(g: Grid, beatIndex: Long): FloatArray {
        if (beatIndex < g.countInBeats) return countIn
        val b = beatIndex - g.countInBeats
        if (b % 4 != 0L) return beat
        val bar = g.pattern[((b / 4) % g.pattern.size).toInt()]
        return if (bar == BarKind.SIXTEENTHS) accentHigh else accentLow
    }

    /**
     * A click: a softly saturated sine (close to a square wave) that fades out.
     * Same peak as a pure sine but much more energy and brightness, so it stays
     * audible over a kick drum.
     */
    private fun tone(freq: Double, seconds: Double, gain: Double): FloatArray {
        val n = (sampleRate * seconds).toInt()
        val norm = tanh(DRIVE)
        return FloatArray(n) { i ->
            val t = i.toDouble() / sampleRate
            val attack = (i / (sampleRate * 0.002)).coerceAtMost(1.0)
            (tanh(DRIVE * sin(2 * PI * freq * t)) / norm * gain * attack * exp(-t * 3.0 / seconds)).toFloat()
        }
    }

    /** Soft limiter: overlapping sounds (a click under a chime) don't clip harshly. */
    private fun limit(x: Float): Float {
        val a = kotlin.math.abs(x)
        if (a <= 0.9f) return x
        return kotlin.math.sign(x) * (0.9f + 0.1f * tanh((a - 0.9) / 0.1).toFloat())
    }

    private companion object {
        const val DRIVE = 3.0
    }

    private fun concat(a: FloatArray, b: FloatArray): FloatArray = a + FloatArray((sampleRate * 0.03).toInt()) + b
}
