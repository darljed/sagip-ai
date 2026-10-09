package dev.darl.sagip.llm

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession.LlmInferenceSessionOptions
import com.google.mediapipe.tasks.genai.llminference.ProgressListener
import java.io.File
// ModelConfig is in the same package (dev.darl.sagip.llm) — no import needed.

/**
 * Thin wrapper over MediaPipe's on-device LLM Inference API (tasks-genai 0.10.27).
 *
 * API shape in this version:
 *  - [LlmInference] (the "engine") holds model path + maxTokens + maxTopK.
 *  - [LlmInferenceSession] holds sampling params (temperature, topK, topP) and the
 *    actual generate calls. Streaming is via a [ProgressListener] passed to
 *    generateResponseAsync.
 *
 * Model-agnostic: point [modelPath] at gemma3-1b-it-int4.task now, or a Gemma 4
 * E2B .task/.litertlm later — only the file changes.
 *
 * The model is NOT bundled in the APK (too large); during development it is pushed
 * to /data/local/tmp/llm/ via `adb push`. ponytail: adb push is the laziest path
 * to a green on-device demo; production would download to app-private storage.
 */
class LlmEngine private constructor(
    private val engine: LlmInference,
    private val temperature: Float,
    private val topK: Int,
    private val topP: Float,
) {

    /** Blocking single-shot generation (creates a one-off session). */
    fun generate(prompt: String): String {
        newSession().use { session ->
            session.addQueryChunk(prompt)
            return session.generateResponse()
        }
    }

    /**
     * Streaming generation. [onPartial] receives each incremental chunk; the
     * second arg is `done`. Runs on MediaPipe's worker; callers should marshal
     * UI updates back to the main thread. Repetition-collapse handling lives in
     * the caller (ViewModel), which owns the accumulated text.
     */
    fun generateAsync(prompt: String, onPartial: (String, Boolean) -> Unit) {
        val session = newSession()
        session.addQueryChunk(prompt)
        val listener = ProgressListener<String> { partial, done ->
            onPartial(partial, done)
            if (done) runCatching { session.close() }
        }
        session.generateResponseAsync(listener)
    }

    private fun newSession(): LlmInferenceSession {
        val opts = LlmInferenceSessionOptions.builder()
            .setTemperature(temperature)
            .setTopK(topK)
            .setTopP(topP)
            .build()
        return LlmInferenceSession.createFromOptions(engine, opts)
    }

    fun close() = engine.close()

    companion object {
        const val DEFAULT_MODEL_PATH = "/data/local/tmp/llm/gemma3-1b-it-int4.task"

        /** True if ANY configured model variant is present on the device. */
        fun modelExists(path: String? = null): Boolean =
            if (path != null) File(path).exists() else ModelConfig.resolve() != null

        /** The model that will actually load (preferred present variant), or null. */
        fun resolvedModel(): ModelConfig? = ModelConfig.resolve()

        /**
         * Create an engine. If [modelPath] is null, auto-resolves the preferred
         * on-device variant via [ModelConfig].
         *
         * Sampling tuned for a 1B model to reduce repetition collapse:
         *  - [topP] nucleus sampling + modest [temperature] break deterministic loops,
         *  - the ViewModel's repetition guard stops any residual loop and trims it.
         * maxTokens=1024 (input+output): 512 was too tight — a 2-chunk prompt (~330
         * input tokens) left too little output budget and produced empty answers.
         * @throws IllegalStateException if no model file is present.
         */
        fun create(
            context: Context,
            modelPath: String? = null,
            maxTokens: Int = 1024,
            topK: Int = 40,
            topP: Float = 0.9f,
            temperature: Float = 0.7f,
        ): LlmEngine {
            val resolvedPath = modelPath
                ?: ModelConfig.resolve()?.path
                ?: error("No model found in ${ModelConfig.LLM_DIR}. Push a .task with adb.")
            check(File(resolvedPath).exists()) {
                "Model not found at $resolvedPath. Push it with: adb push <file> $resolvedPath"
            }
            val engineOptions = LlmInferenceOptions.builder()
                .setModelPath(resolvedPath)
                .setMaxTokens(maxTokens)
                .setMaxTopK(topK)
                .build()
            val engine = LlmInference.createFromOptions(context, engineOptions)
            return LlmEngine(engine, temperature, topK, topP)
        }
    }
}
