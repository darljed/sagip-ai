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
        // The reference guidance + title must be present, above the output instruction.
        assertTrue(p.contains("Reference guidance from trusted sources"))
        assertTrue(p.contains("Severe bleeding (hemorrhage)"))
        assertTrue(p.contains("Apply firm pressure"))
        assertTrue(p.indexOf("Apply firm pressure") < p.indexOf("Answer the person's actual question"))
    }

    @Test fun outputInstructsGroundedAnswer() {
        val p = PromptBuilder.build("bleeding", UserProfile.DEMO, listOf(bleedingCritical))
        // Grounded generation: model answers the actual question, grounded in guidance.
        assertTrue(p.contains("Answer the person's actual question"))
        assertTrue(p.contains("Base your answer on the reference guidance"))
        assertTrue(p.contains("Do not invent medical facts"))
    }

    @Test fun languageInstructionIsLast_forRecency() {
        val p = PromptBuilder.build("bleeding", UserProfile.DEMO, listOf(bleedingCritical))
        val langIdx = p.indexOf("entire answer in Tagalog")
        assertTrue("language instruction present", langIdx > 0)
        assertTrue("language instruction is near the end", langIdx > p.length - 120)
    }

    @Test fun allergyNoteInjected_whenFlaggedAndGuidanceInvolvesMedicine() {
        val withMed = bleedingCritical.copy(text = "1. Give one adult aspirin to chew.")
        val p = PromptBuilder.build("bleeding", UserProfile.DEMO, listOf(withMed))
        assertTrue(p.contains("Allergic to penicillin"))
    }

    @Test fun allergyNoteOmitted_whenGuidanceHasNoMedicine() {
        // Prevents small-model nonsense like "seafood allergy" inside a snake answer.
        val p = PromptBuilder.build("bleeding", UserProfile.DEMO, listOf(bleedingCritical))
        assertFalse(p.contains("Allergic to"))
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
        val tl = PromptBuilder.build("bleeding", UserProfile.DEMO, listOf(bleedingCritical))
        assertTrue(tl.contains("life-threatening"))
        val en = PromptBuilder.build("bleeding", UserProfile.DEMO.copy(preferredLanguage = Lang.EN), listOf(bleedingCritical))
        assertTrue(en.contains("life-threatening"))
    }

    @Test fun instructsAgainstRepeating() {
        val p = PromptBuilder.build("x", UserProfile.DEMO, listOf(bleedingCritical))
        assertTrue("warns against repetition", p.contains("Do not repeat any line"))
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
        assertFalse(p.contains("Personal facts about this person"))
        assertTrue(p.contains("Reference guidance from trusted sources"))
    }

    @Test fun addressesPersonByFirstName_noPetNames() {
        val p = PromptBuilder.build("bleeding", UserProfile(name = "Juan Dela Cruz"), listOf(bleedingCritical))
        assertTrue(p.contains("name is Juan"))
        assertTrue(p.contains("Never call them 'Mahal'"))
        assertFalse(p.contains("Dela Cruz"))
    }
}
