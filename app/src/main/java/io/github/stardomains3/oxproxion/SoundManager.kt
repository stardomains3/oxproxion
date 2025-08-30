package io.github.stardomains3.oxproxion

import android.content.Context

class SoundManager(context: Context) {

    /*private val sharedPreferencesHelper = SharedPreferencesHelper(context)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)

    fun playSuccessTone() {
        if (isSoundOn()) {
           // toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 200)
            toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 140)
            // TONE_PROP_PROMPT
          //  TONE_PROP_BEEP
           // TONE_SUP_DIAL
           // TONE_SUP_CONFIRM good
           // TONE_CDMA_ONE_MIN_BEEP no
           // TONE_CDMA_ABBR_ALERT loud
            //TONE_CDMA_SIGNAL_OFF


        }
    }

    fun playErrorTone() {
        if (isSoundOn()) {
            toneGenerator.startTone(ToneGenerator.TONE_CDMA_SOFT_ERROR_LITE, 200)
        }
    }

    fun playCancelTone() {
        if (isSoundOn()) {
            toneGenerator.startTone(ToneGenerator.TONE_CDMA_ABBR_INTERCEPT, 200)
        }
    }

    private fun isSoundOn(): Boolean {
        return sharedPreferencesHelper.getSoundPreference()
    }
    fun release() {
        toneGenerator.release()
    }*/
}
