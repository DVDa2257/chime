package com.example.speedchime

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log

/** Toca o chime: sintetizado (como na pagina de teste) ou um arquivo de audio escolhido. */
class ChimePlayer(private val ctx: Context) {

    private val handler = Handler(Looper.getMainLooper())
    private val sampleRate = 44100
    private var cacheKey = ""
    private var cache: ShortArray? = null

    private val attrs: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    fun play(cfg: Cfg) {
        if (cfg.useFile && !cfg.fileUri.isNullOrEmpty()) {
            if (playFile(cfg)) return
        }
        playSynth(cfg)
    }

    private fun playFile(cfg: Cfg): Boolean {
        return try {
            val mp = MediaPlayer()
            mp.setAudioAttributes(attrs)
            mp.setDataSource(ctx, Uri.parse(cfg.fileUri))
            mp.setVolume(cfg.vol, cfg.vol)
            mp.setOnPreparedListener { it.start() }
            mp.setOnCompletionListener { it.release() }
            mp.setOnErrorListener { p, _, _ ->
                p.release()
                true
            }
            mp.prepareAsync()
            true
        } catch (e: Exception) {
            Log.e("SpeedChime", "erro ao tocar arquivo", e)
            false
        }
    }

    private fun playSynth(cfg: Cfg) {
        val key = "${cfg.freq}|${cfg.decayCs}|${cfg.brightPct}|${cfg.clickPct}|${cfg.volPct}|${cfg.ponGapMs}|${cfg.ponPct}"
        var data = cache
        if (data == null || key != cacheKey) {
            data = synth(cfg)
            cache = data
            cacheKey = key
        }
        try {
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
            val track = AudioTrack.Builder()
                .setAudioAttributes(attrs)
                .setAudioFormat(format)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(data.size * 2)
                .build()
            track.write(data, 0, data.size)
            track.play()
            val ms = data.size * 1000L / sampleRate + 300L
            handler.postDelayed({
                try {
                    track.release()
                } catch (_: Exception) {
                }
            }, ms)
        } catch (e: Exception) {
            Log.e("SpeedChime", "erro ao tocar chime sintetizado", e)
        }
    }

    /** Pin-pon: dois toques; o segundo ("pon") e mais grave e vem depois do intervalo ajustavel. */
    private fun synth(cfg: Cfg): ShortArray {
        val ratios = doubleArrayOf(1.0, 2.756, 5.404, 8.933, 13.34)
        val strikeLen = Math.max((cfg.decay * sampleRate).toInt(), sampleRate / 20) + sampleRate / 20
        val offset = (cfg.ponGapMs / 1000.0 * sampleRate).toInt()
        val total = offset + strikeLen
        val out = DoubleArray(total)

        fun strike(baseFreq: Double, start: Int) {
            for (i in ratios.indices) {
                val f = baseFreq * ratios[i]
                if (f > 16000.0) continue
                val amp = 0.22 * Math.pow(cfg.bright.toDouble(), i.toDouble())
                if (amp < 0.002) continue
                val d = Math.max(0.05, cfg.decay / (1.0 + i * 0.9))
                val n = Math.min(strikeLen, (d * sampleRate).toInt() + sampleRate / 20)
                for (s in 0 until n) {
                    val t = s.toDouble() / sampleRate
                    val env = if (t < 0.002) t / 0.002 else Math.exp(-6.9 * (t - 0.002) / d)
                    out[start + s] += amp * env * Math.sin(2.0 * Math.PI * f * t)
                }
            }
            if (cfg.click > 0.01f) {
                val rnd = java.util.Random(1)
                val n = Math.min(strikeLen, (0.025 * sampleRate).toInt())
                var prev = 0.0
                for (s in 0 until n) {
                    val white = rnd.nextDouble() * 2.0 - 1.0
                    val hp = white - prev
                    prev = white
                    val env = Math.exp(-6.9 * s.toDouble() / n)
                    out[start + s] += cfg.click * 0.25 * hp * env
                }
            }
        }

        strike(cfg.freq.toDouble(), 0)
        strike(cfg.freq * cfg.ponRatio, offset)

        var peak = 0.0
        for (v in out) peak = Math.max(peak, Math.abs(v))
        val norm = if (peak > 0.95) 0.95 / peak else 1.0

        return ShortArray(total) { idx ->
            val v = out[idx] * norm * cfg.vol * 32767.0
            v.toInt().coerceIn(-32768, 32767).toShort()
        }
    }
}
