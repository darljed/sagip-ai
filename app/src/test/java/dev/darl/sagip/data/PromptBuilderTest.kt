package dev.darl.sagip.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 4-prep self-check — prompt builder personalization injection, no model.
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

    @Test fun allergyLineAppears_whenChunkFlagsAllergies() {
        val p = PromptBuilder.build("bleeding", UserProfile.DEMO, listOf(bleedingCritical))
        assertTrue("allergy fact in profile block", p.contains("Allergies: penicillin"))
        assertTrue("allergy warning rule", p.contains("allergic to penicillin"))
    }

    @Test fun bloodTypeAppears_whenRelevant() {
        val p = PromptBuilder.build("bleeding", UserProfile.DEMO, listOf(bleedingCritical))
        assertTrue(p.contains("Blood type: O+"))
    }

    @Test fun householdLineAppears_whenChunkFlagsHousehold() {
        val p = PromptBuilder.build("evacuate", UserProfile.DEMO, listOf(evacUrgent))
        assertTrue("household in profile block", p.contains("Household: infant"))
        assertTrue("household tailoring rule", p.contains("household includes: infant"))
    }

    @Test fun emergencyContactLineAppears_whenCallEmergency() {
        val p = PromptBuilder.build("evacuate", UserProfile.DEMO, listOf(evacUrgent))
        assertTrue("contact in profile", p.contains("Maria (+639171234567)"))
        assertTrue("call-contact rule", p.contains("Call Maria at +639171234567"))
    }

    @Test fun criticalTriggersSeverityBannerInstruction() {
        val p = PromptBuilder.build("bleeding", UserProfile.DEMO, listOf(bleedingCritical))
        assertTrue(p.contains("life-threatening"))
    }

    @Test fun groundingInstructionAlwaysPresent() {
        val p = PromptBuilder.build("x", UserProfile.DEMO, listOf(bleedingCritical))
        assertTrue("must instruct answer only from guidance", p.contains("ONLY from the GUIDANCE"))
        assertTrue("must forbid inventing advice", p.contains("do NOT invent"))
    }

    @Test fun sourceIsCited() {
        val p = PromptBuilder.build("bleeding", UserProfile.DEMO, listOf(bleedingCritical))
        assertTrue(p.contains("Philippine Red Cross / WHO"))
    }

    @Test fun respondsInTagalog_whenProfilePrefersTl() {
        val p = PromptBuilder.build("bleeding", UserProfile.DEMO, listOf(bleedingCritical))
        assertTrue("DEMO prefers TL", p.contains("Respond in Tagalog"))
    }

    @Test fun irrelevantFactsDoNotLeak() {
        // bleeding chunk flags blood_type+allergies but NOT household/home/contact.
        val p = PromptBuilder.build("bleeding", UserProfile.DEMO, listOf(bleedingCritical))
        assertFalse("home should not appear (not flagged)", p.contains("Barangay San Roque"))
        assertFalse("household should not appear (not flagged)", p.contains("Household: infant"))
    }

    @Test fun emptyProfile_noPersonalizationBlock_butStillGrounded() {
        val p = PromptBuilder.build("bleeding", UserProfile(), listOf(bleedingCritical))
        assertFalse(p.contains("USER PROFILE"))
        assertTrue(p.contains("ONLY from the GUIDANCE"))
    }
}
