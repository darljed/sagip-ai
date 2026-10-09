package dev.darl.sagip.llm

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Thin wrapper over Google's LiteRT-LM Kotlin runtime (the runtime Gemma 4 ships on).
 *
 * Why not MediaPipe tasks-genai: it needs hand-rolled chat templates (Gemma 4 uses
 * `<|turn>` markers, not Gemma 3's) and ran ~10x slower on CPU. LiteRT-LM's
 * [Conversation] applies the model's own embedded template, supports a system
 * instruction, and has a real GPU backend.
 *
 * One fresh [Conversation] per question (independent emergencies must not share
 * context). Lock-free: native calls are never made while holding a monitor, and a
 * conversation is never closed from inside its own callback.
 */
class LlmEngine private constructor(
    private val engine: Engine,
    val backendLabel: String,
    private val systemInstruction: String,
    private val sampler: SamplerConfig,
) {
    private val active = AtomicReference<Conversation?>(null)
    private val activeDone = AtomicBoolean(true)

    /**
     * Streaming generation. [onPartial] gets each incremental text chunk and a `done`
     * flag (exactly one done=true is delivered, on completion or error).
     */
    fun generateAsync(prompt: String, onPartial: (String, Boolean) -> Unit) {
        retire(active.getAndSet(null))
        val conv = engine.createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(systemInstruction),
                samplerConfig = sampler,
            )
        )
        activeDone.set(false)
        active.set(conv)
        conv.sendMessageAsync(prompt, object : MessageCallback {
            override fun onMessage(message: Message) {
                val t = message.toString()
                if (t.isNotEmpty()) onPartial(t, false)
            }

            override fun onDone() {
                activeDone.set(true)
                onPartial("", true)
            }

            override fun onError(throwable: Throwable) {
                activeDone.set(true)
                android.util.Log.e("SagipLlm", "generation error: ${throwable.message}")
                onPartial("", true)
            }
        })
    }

    /** Cancel + close the in-flight conversation. Safe when idle. */
    fun cancel() = retire(active.getAndSet(null))

    private fun retire(c: Conversation?) {
        c ?: return
        // Only cancel work that is still running; cancelling a finished one has
        // corrupted native state before.
        if (!activeDone.get()) runCatching { c.cancelProcess() }
        runCatching { c.close() }
        activeDone.set(true)
    }

    fun close() {
        cancel()
        runCatching { engine.close() }
    }

    companion object {
        /** Grounding rules live in the system turn so the user turn stays short. */
        const val DEFAULT_SYSTEM =
            "You are SAGIP, a calm, caring support agent inside an offline emergency and survival app " +
                "for people in the Philippines. You help people with their concerns by explaining the " +
                "trusted reference guidance you are given, adapted to what they actually asked. " +
                "Never invent medical facts or numbers; if the guidance does not cover it, say so " +
                "briefly and suggest calling 911 or the barangay for urgent danger."

        fun modelExists(path: String? = null): Boolean =
            if (path != null) File(path).exists() else ModelConfig.resolve() != null

        fun resolvedModel(): ModelConfig? = ModelConfig.resolve()

        /**
         * Create + initialise an engine (call off the main thread; load takes seconds).
         * GPU is the default; `touch /data/local/tmp/llm/force_cpu` forces CPU.
         */
        fun create(
            context: Context,
            modelPath: String? = null,
            system: String = DEFAULT_SYSTEM,
            temperature: Double = 0.4,
            topK: Int = 40,
            topP: Double = 0.9,
        ): LlmEngine {
            val path = modelPath
                ?: ModelConfig.resolve()?.path
                ?: error("No model found in ${ModelConfig.LLM_DIR}.")
            check(File(path).exists()) { "Model not found at $path" }
            val cpu = File("${ModelConfig.LLM_DIR}/force_cpu").exists()
            val backend = if (cpu) Backend.CPU() else Backend.GPU()
            val engine = Engine(
                EngineConfig(
                    modelPath = path,
                    backend = backend,
                    cacheDir = context.cacheDir.path,
                )
            )
            engine.initialize()
            return LlmEngine(engine, if (cpu) "CPU" else "GPU", system, SamplerConfig(topK, topP, temperature, 0))
        }
    }
}
