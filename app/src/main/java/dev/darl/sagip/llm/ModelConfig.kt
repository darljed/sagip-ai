package dev.darl.sagip.llm

import java.io.File

/**
 * Central model registry so the model choice is a one-line flip, not scattered code.
 *
 * Device reality (this build): Gemma 4 E2B (2.6GB) OOMs on the 8GB S23 Ultra —
 * only ~2.7GB RAM is free, and the model needs more to load. So **Gemma 3 1B INT4
 * (~584MB) is the preferred model here** for demo stability. Gemma 4 E2B stays
 * defined (and tried as a secondary) so a higher-RAM device would still use it, and
 * so the upgrade is a pure preference-order change.
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

        /** Preference order: Gemma 3n E2B first (needs mediapipe genai >= 0.10.35 to
         *  parse its audio-adapter .litertlm; 0.10.27 aborted with "Unknown model type:
         *  tf_lite_audio_adapter"). Falls back to the stable 1B if E2B isn't present. */
        val PREFERENCE = listOf(GEMMA4_E2B, GEMMA3N_E2B, GEMMA3_1B)

        /** The model to actually load: first preferred variant present on device, or null. */
        fun resolve(): ModelConfig? = PREFERENCE.firstOrNull { it.exists() }
    }
}
