package com.doublebasshero.audio

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Voice prompts (text-to-speech) and vibration, so a session works without looking. */
class Speaker(context: Context) {
    private var ready = false
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext, ::onInit)

    private fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (!ready) return
        tts.language = Locale.US
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
    }

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun say(text: String) {
        if (ready) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "dbh")
    }

    fun buzz() {
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 200, 100, 200), -1))
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
