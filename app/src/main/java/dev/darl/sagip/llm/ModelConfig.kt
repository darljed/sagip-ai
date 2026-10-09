package dev.darl.sagip.llm

import java.io.File

/**
 * Central model registry so the model choice is a one-line flip, not scattered code.
 *
 * Primary: **Gemma 4 E2B** (`gemma-4-e2b-it.litertlm`, ~2.4 GiB) running on the phone GPU through
 * LiteRT-LM — first token in ~1 s on a Galaxy S23 Ultra. Fallbacks (used only if the file is not
 * on the device): Gemma 3n E2B, then Gemma 3 1B INT4 (~560 MB) for low-RAM phones.
 * The file is too large for git; push it with `scripts/push-model.sh` (see README).
 */
enum class ModelConfig(
    val displayName: String,
    val fileName: String,
    /** Rough RAM headroom (GB) the load needs; used to warn, not hard-gate. */
    val minFreeGb: Int,
) {
    GEMMA3_1B("Gemma 3 1B", "gemma3-1b-it-int4.litertlm", minFreeGb = 2),
    GEMMA4_E2B("Gemma 4 E2B", "gemma-4-e2b-it.litertlm", minFreeGb = 4),
    GEMMA3N_E2B("Gemma 3n E2B", "gemma-3n-e2b-it-int4.litertlm", minFreeGb = 6);

    val path: String get() = "$LLM_DIR/$fileName"

    fun exists(): Boolean = File(path).exists()

    companion object {
        const val LLM_DIR = "/data/local/tmp/llm"

        /** Preference order: best model first; the first one present on the device wins. */
        val PREFERENCE = listOf(GEMMA4_E2B, GEMMA3N_E2B, GEMMA3_1B)

        /** The model to actually load: first preferred variant present on device, or null. */
        fun resolve(): ModelConfig? = PREFERENCE.firstOrNull { it.exists() }
    }
}
