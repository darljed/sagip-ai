package dev.darl.sagip.llm

import java.io.File

/**
 * Central model registry so the H1:30 "model gate" in PLAN.md is a one-line flip,
 * not a code change scattered across files.
 *
 * Strategy:
 *  - [PRIMARY] = Gemma 4 E2B (best Tagalog, newest). [FALLBACK] = Gemma 3 1B INT4
 *    (proven, smaller). [resolve] auto-picks whichever .task is actually on the
 *    device, preferring PRIMARY — so dropping a Gemma 4 file onto the phone
 *    switches the app with no rebuild.
 */
enum class ModelConfig(
    val displayName: String,
    val fileName: String,
) {
    GEMMA4_E2B("Gemma 4 E2B", "gemma-4-e2b-it-int4.task"),
    GEMMA3_1B("Gemma 3 1B INT4", "gemma3-1b-it-int4.task");

    val path: String get() = "$LLM_DIR/$fileName"

    fun exists(): Boolean = File(path).exists()

    companion object {
        const val LLM_DIR = "/data/local/tmp/llm"

        /** Preference order for the gate: try Gemma 4 first, fall back to Gemma 3. */
        val PREFERENCE = listOf(GEMMA4_E2B, GEMMA3_1B)

        /** The model to actually load: first preferred variant present on device, or null. */
        fun resolve(): ModelConfig? = PREFERENCE.firstOrNull { it.exists() }
    }
}
