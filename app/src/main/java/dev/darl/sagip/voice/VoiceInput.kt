package dev.darl.sagip.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * On-device voice input via Android [SpeechRecognizer] (prefers OFFLINE recognition).
 * Must be created and driven on the MAIN thread.
 *
 * Why the earlier version was intermittent, and what changed:
 *  - Callbacks of a destroyed/replaced recognizer (ERROR_CLIENT after destroy(), late results) leaked into the
 *    new session and flipped the UI back to "not listening" → every callback is now tagged with a session id and
 *    ignored when stale.
 *  - onEndOfSpeech used to report "not listening" before the transcript arrived, so a second tap started a new
 *    session on top of the first (ERROR_RECOGNIZER_BUSY). The UI now stays "listening" until a result or error,
 *    and BUSY is retried once after a short delay.
 *  - Engines often finish with NO_MATCH / SPEECH_TIMEOUT right after producing good partials; the last partial
 *    is now delivered as the result instead of an error.
 *  - Silence thresholds are set so the recognizer finalises ~1.3 s after you stop talking (drives auto-send).
 */
class VoiceInput(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null
    private var session = 0
    private val requested = HashSet<String>()   // languages whose offline pack download was requested
    private val main = Handler(Looper.getMainLooper())

    val isAvailable: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    /** One way of recognising: which engine, which language, offline or not. */
    private data class Attempt(val onDevice: Boolean, val tag: String, val preferOffline: Boolean)

    /**
     * Build the fallback chain. The phone's DEFAULT recognizer (Google TTS service here) reports
     * LANGUAGE_NOT_SUPPORTED/UNAVAILABLE (12/11) whenever its offline pack is missing and the
     * request can't go online — which is why voice felt random. So: the on-device recognizer first
     * (Android 13+, uses installed packs and can download them), then the default service online.
     * Also "en-PH" is not a recognised locale on most builds: English uses en-US.
     */
    private fun chain(languageTag: String): List<Attempt> {
        val tag = if (languageTag.startsWith("en", true)) "en-US" else languageTag
        val out = ArrayList<Attempt>()
        if (android.os.Build.VERSION.SDK_INT >= 33 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            out += Attempt(true, tag, true)
        }
        out += Attempt(false, tag, false)
        if (tag != "en-US") out += Attempt(false, "en-US", false)
        return out
    }

    fun start(
        languageTag: String,
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onState: (listening: Boolean) -> Unit,
        onError: (String) -> Unit,
    ) {
        if (!isAvailable && !(android.os.Build.VERSION.SDK_INT >= 33 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context))) {
            onError("Voice input not available on this device"); return
        }
        run(chain(languageTag), 0, retryBusy = true, languageTag, onPartial, onFinal, onState, onError)
    }

    private fun intentFor(a: Attempt) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, a.tag)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, a.preferOffline)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        // End-of-speech detection (honoured by most engines): finalise ~1.3 s after you stop talking.
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1300L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1100L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 600L)
    }

    private fun run(
        attempts: List<Attempt>,
        index: Int,
        retryBusy: Boolean,
        requestedTag: String,
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onState: (listening: Boolean) -> Unit,
        onError: (String) -> Unit,
    ) {
        stop()
        val attempt = attempts[index]
        val id = ++session
        val sr = if (attempt.onDevice && android.os.Build.VERSION.SDK_INT >= 33)
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context) else SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = sr
        var lastPartial = ""
        fun live() = id == session
        fun next() {
            if (index + 1 < attempts.size) run(attempts, index + 1, true, requestedTag, onPartial, onFinal, onState, onError)
            else {
                onState(false)
                // Nothing worked: ask the system to fetch the offline pack, and tell the user.
                if (android.os.Build.VERSION.SDK_INT >= 33 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                    runCatching {
                        val dl = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                        dl.triggerModelDownload(intentFor(Attempt(true, attempts.first().tag, true)))
                    }
                }
                onError("NEEDS_PACK")
            }
        }

        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { if (live()) onState(true) }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            // Stay "listening" until the transcript (or an error) arrives.
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onPartialResults(partialResults: Bundle?) {
                if (!live()) return
                partialResults?.firstText()?.let { lastPartial = it; onPartial(it) }
            }

            override fun onResults(results: Bundle?) {
                if (!live()) return
                onState(false)
                val text = results?.firstText() ?: lastPartial.takeIf { it.isNotBlank() }
                if (text != null) onFinal(text) else onError("Didn't catch that — try again")
            }

            override fun onError(error: Int) {
                android.util.Log.w("SagipVoice", "error=$error attempt=$index/${attempts.size} onDevice=${attempt.onDevice} tag=${attempt.tag} offline=${attempt.preferOffline} live=${live()} partial='${lastPartial.take(20)}'")
                if (!live()) return
                when {
                    error == SpeechRecognizer.ERROR_CLIENT -> onState(false)

                    // Previous session still shutting down: retry this attempt once shortly.
                    error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY && retryBusy -> {
                        main.postDelayed({
                            if (live()) run(attempts, index, false, requestedTag, onPartial, onFinal, onState, onError)
                        }, 450)
                    }

                    // Heard something but the engine gave up: use what we have.
                    (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) &&
                        lastPartial.isNotBlank() -> { onState(false); onFinal(lastPartial) }

                    isPackOrNetwork(error) -> {
                        // On-device pack missing (13/12): ask the system to download it now, once per
                        // language, so voice also works with NO connection from the next time on.
                        if (attempt.onDevice && (error == 13 || error == 12) && requested.add(attempt.tag) &&
                            android.os.Build.VERSION.SDK_INT >= 33
                        ) {
                            runCatching {
                                SpeechRecognizer.createOnDeviceSpeechRecognizer(context).triggerModelDownload(intentFor(attempt))
                            }
                        }
                        next()
                    }

                    else -> { onState(false); onError(errorText(error)) }
                }
            }
        })
        sr.startListening(intentFor(attempt))
    }

    /** Stop and invalidate every pending callback of the current session. Safe to call any time. */
    fun stop() {
        session++
        main.removeCallbacksAndMessages(null)
        recognizer?.let { r ->
            runCatching { r.stopListening() }
            runCatching { r.destroy() }
        }
        recognizer = null
    }

    private fun isPackOrNetwork(error: Int) =
        error in intArrayOf(11, 12, 13) ||
            error == SpeechRecognizer.ERROR_NETWORK ||
            error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT ||
            error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ||
            error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED

    private fun Bundle.firstText(): String? =
        getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }

    private fun errorText(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that — try again"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech heard — tap the mic and try again"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission needed"
        SpeechRecognizer.ERROR_AUDIO -> "Microphone is busy or unavailable"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Voice is busy — try again"
        else -> "Voice error ($code)"
    }
}
