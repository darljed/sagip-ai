package dev.darl.sagip.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PromptBuilder self-check — small-model-tuned prompt. No model needed.
 * Run: ./gradlew :app:testDebugUnitTest --tests "*PromptBuilderTest*"
 */
class PromptBuilderTest {

    private val bleedingCritical = Chunk(
        id = "firstaid.bleeding.severe.001", pack = "first_aid", topic = "severe_bleeding",
        lang = "en", title = "Severe bleeding (hemorrhage)", severity = Severity.CRITICAL,
        tags = listOf("bleeding", "dugo"), text = "1. Apply firm pressure...",
        source = "Philippine Red Cross / WHO", personalize = listOf("blood_type", "allergies"),
        callEmergency = true,
    )
    private val evacUrgent = Chunk(
        id = "typhoon.evacuate.when.001", pack = "typhoon_flood", topic = "when_to_evacuate",
        lang = "en", title = "When and how to evacuate", severity = Severity.URGENT,
        tags = listOf("evacuate", "lumikas"), text = "1. Evacuate immediately if...",
        source = "NDRRMC", personalize = listOf("household_infant", "emergency_contact"),
        callEmergency = true,
    )

    @Test fun guidanceStepsAreFrontAndCenter() {
        val p = PromptBuilder.build("bleeding", UserProfile.DEMO, listOf(bleedingCritical))
        // The actual steps + title must be in the prompt, under the "Use ONLY these steps" lead.
        assertTrue(p.contains("Use ONLY these official steps"))
        assertTrue(p.contains("Severe bleeding (hemorrhage)"))
        assertTrue(p.contains("Apply firm pressure"))
        // Guidance should appear BEFORE the output-format instruction (front-and-center).
        assertTrue(p.indexOf("Apply firm pressure") < p.indexOf("numbered steps"))
    }

    @Test fun outputFormatInstructsNumberedSteps() {
        val p = PromptBuilder.build("bleeding", UserProfile.DEMO, listOf(bleedingCritical))
        assertTrue(p.contains("numbered steps"))
    }

    @Test fun languageInstructionIsLast_forRecency() {
        val p = PromptBuilder.build("bleeding", UserProfile.DEMO, listOf(bleedingCritical))
        // DEMO prefers TL; the language line must come near the very end (just before "Answer:").
        val langIdx = p.indexOf("entire answer in Tagalog")
        assertTrue("language instruction present", langIdx > 0)
        assertTrue("language instruction is near the end", langIdx > p.length - 120)
    }

    @Test fun allergyNoteInjected_whenFlagged() {
        val p = PromptBuilder.build("bleeding", UserProfile.DEMO, listOf(bleedingCritical))
        assertTrue(p.contains("Allergic to penicillin"))
    }

    @Test fun householdNoteInjected_whenFlagged() {
        val p = PromptBuilder.build("evacuate", UserProfile.DEMO, listOf(evacUrgent))
        assertTrue(p.contains("Household has infant"))
    }

    @Test fun emergencyContactHint_whenCallEmergency() {
        val p = PromptBuilder.build("evacuate", UserProfile.DEMO, listOf(evacUrgent))
        assertTrue(p.contains("Maria") && p.contains("+639171234567"))
    }

    @Test fun criticalAddsEmergencyLeadInstruction() {
        // DEMO is TL -> lead-in asks for a Tagalog warning line.
        val tl = PromptBuilder.build("bleeding", UserProfile.DEMO, listOf(bleedingCritical))
        assertTrue(tl.contains("life-threatening emergency"))
        // EN profile -> English lead-in.
        val en = PromptBuilder.build("bleeding", UserProfile.DEMO.copy(preferredLanguage = Lang.EN), listOf(bleedingCritical))
        assertTrue(en.contains("this is an emergency"))
    }

    @Test fun groundingPresent_butTerse() {
        val p = PromptBuilder.build("x", UserProfile.DEMO, listOf(bleedingCritical))
        assertTrue(p.contains("Use only the steps above"))
        assertTrue(p.contains("don't have that info"))
    }

    @Test fun sourceNamed() {
        val p = PromptBuilder.build("bleeding", UserProfile.DEMO, listOf(bleedingCritical))
        assertTrue(p.contains("Philippine Red Cross / WHO"))
    }

    @Test fun englishProfile_getsEnglishInstruction() {
        val enProfile = UserProfile.DEMO.copy(preferredLanguage = Lang.EN)
        val p = PromptBuilder.build("bleeding", enProfile, listOf(bleedingCritical))
        assertTrue(p.contains("entire answer in English"))
        assertFalse(p.contains("Do not use English"))
    }

    @Test fun irrelevantFactsDoNotLeak() {
        val p = PromptBuilder.build("bleeding", UserProfile.DEMO, listOf(bleedingCritical))
        // bleeding flags blood_type+allergies, NOT household/home.
        assertFalse(p.contains("Barangay San Roque"))
        assertFalse(p.contains("Household has"))
    }

    @Test fun emptyProfile_noPersonalBlock_butStillGrounded() {
        val p = PromptBuilder.build("bleeding", UserProfile(), listOf(bleedingCritical))
        assertFalse(p.contains("Personalise for this person"))
        assertTrue(p.contains("Use only the steps above"))
    }
}
