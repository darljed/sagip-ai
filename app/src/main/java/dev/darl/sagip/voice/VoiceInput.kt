package dev.darl.sagip.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * On-device voice input via Android [SpeechRecognizer]. Prefers OFFLINE recognition
 * (EXTRA_PREFER_OFFLINE) so it fits SAGIP's offline-first promise — on devices with the
 * language pack downloaded, it transcribes with no network.
 *
 * Must be created and driven on the MAIN thread. Caller supplies the BCP-47 [languageTag]
 * (e.g. "en-PH" or "fil-PH") and receives partial + final transcripts plus listening state.
 *
 * ponytail: the platform recognizer is the laziest correct path — no model to bundle, and
 * it already honours the user's downloaded offline language packs. Graceful if unavailable.
 */
class VoiceInput(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null
    val isAvailable: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun start(
        languageTag: String,
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onState: (listening: Boolean) -> Unit,
        onError: (String) -> Unit,
    ) {
        if (!isAvailable) { onError("Voice input not available on this device"); return }
        startInternal(languageTag, preferOffline = true, allowFallback = true,
            onPartial, onFinal, onState, onError)
    }

    private fun startInternal(
        languageTag: String,
        preferOffline: Boolean,
        allowFallback: Boolean,
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onState: (listening: Boolean) -> Unit,
        onError: (String) -> Unit,
    ) {
        stop()
        val sr = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = sr
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = onState(true)
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() = onState(false)
            override fun onError(error: Int) {
                onState(false)
                // The device often lacks the fil-PH / offline pack. Fall back once to
                // en-US with online allowed, which is installed on virtually all devices.
                val packOrNet = error == SpeechRecognizer.ERROR_NETWORK ||
                    error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT ||
                    error == 12 /* LANGUAGE_PACK_ERROR surfaces as a client error */ ||
                    error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ||
                    error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED
                if (allowFallback && packOrNet) {
                    startInternal("en-US", preferOffline = false, allowFallback = false,
                        onPartial, onFinal, onState, onError)
                } else {
                    onError(errorText(error))
                }
            }
            override fun onResults(results: Bundle?) {
                onState(false)
                results?.firstText()?.let { onFinal(it) }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.firstText()?.let { onPartial(it) }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
        }
        sr.startListening(intent)
    }

    fun stop() {
        recognizer?.run { stopListening(); destroy() }
        recognizer = null
    }

    private fun Bundle.firstText(): String? =
        getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }

    private fun errorText(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that — try again"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech heard"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission needed"
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "Offline voice pack not installed for this language"
        else -> "Voice error ($code)"
    }
}
