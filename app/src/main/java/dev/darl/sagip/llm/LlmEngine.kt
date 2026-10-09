package dev.darl.sagip.llm

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession.LlmInferenceSessionOptions
import com.google.mediapipe.tasks.genai.llminference.ProgressListener
import java.io.File

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
     * UI updates back to the main thread.
     */
    fun generateAsync(prompt: String, onPartial: (String, Boolean) -> Unit) {
        val session = newSession()
        session.addQueryChunk(prompt)
        val listener = ProgressListener<String> { partial, done ->
            onPartial(partial, done)
            if (done) session.close()
        }
        session.generateResponseAsync(listener)
    }

    private fun newSession(): LlmInferenceSession {
        val opts = LlmInferenceSessionOptions.builder()
            .setTemperature(temperature)
            .setTopK(topK)
            .build()
        return LlmInferenceSession.createFromOptions(engine, opts)
    }

    fun close() = engine.close()

    companion object {
        const val DEFAULT_MODEL_PATH = "/data/local/tmp/llm/gemma3-1b-it-int4.task"

        fun modelExists(path: String = DEFAULT_MODEL_PATH): Boolean = File(path).exists()

        /**
         * Create an engine for the model at [modelPath].
         * @throws IllegalStateException if the model file is missing (clear,
         *         demo-safe failure rather than a native crash).
         */
        fun create(
            context: Context,
            modelPath: String = DEFAULT_MODEL_PATH,
            maxTokens: Int = 1024,
            topK: Int = 64,
            temperature: Float = 0.6f,
        ): LlmEngine {
            check(modelExists(modelPath)) {
                "Model not found at $modelPath. Push it with: adb push <file> $modelPath"
            }
            val engineOptions = LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(maxTokens)
                .setMaxTopK(topK)
                .build()
            val engine = LlmInference.createFromOptions(context, engineOptions)
            return LlmEngine(engine, temperature, topK)
        }
    }
}
