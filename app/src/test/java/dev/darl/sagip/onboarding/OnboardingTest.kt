package dev.darl.sagip.onboarding

import dev.darl.sagip.data.Lang
import dev.darl.sagip.data.ProfileStore
import dev.darl.sagip.data.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingTest {

    // --- ProfileStore JSON round-trip (pure, no Android Context) ---
    @Test fun profileRoundTrips() {
        val p = UserProfile.DEMO
        val restored = ProfileStore.fromJson(ProfileStore.toJson(p).toString())
        assertEquals(p, restored)
    }

    @Test fun emptyProfileRoundTrips() {
        val p = UserProfile()
        val restored = ProfileStore.fromJson(ProfileStore.toJson(p).toString())
        assertEquals(p, restored)
    }

    // --- yes/no parser (EN + TL) ---
    @Test fun yesNoParsing() {
        listOf("yes", "oo", "opo", "meron", "Oo may sanggol", "YES").forEach {
            assertTrue("'$it' should be yes", Onboarding.parseYesNo(it))
        }
        listOf("no", "hindi", "wala", "nope", "").forEach {
            assertFalse("'$it' should be no", Onboarding.parseYesNo(it))
        }
    }

    // --- phone parser ---
    @Test fun phoneExtraction() {
        assertEquals("+639171234567", Onboarding.parsePhone("+63 917 123 4567"))
        assertEquals("09171234567", Onboarding.parsePhone("0917-123-4567"))
        assertEquals("", Onboarding.parsePhone("I don't know"))
    }

    // --- list parser ---
    @Test fun listParsing() {
        assertEquals(listOf("penicillin", "aspirin"), Onboarding.parseList("penicillin, aspirin"))
        assertEquals(listOf("asthma", "hypertension"), Onboarding.parseList("asthma and hypertension"))
        assertEquals(emptyList<String>(), Onboarding.parseList("none"))
        assertEquals(emptyList<String>(), Onboarding.parseList("wala"))
    }

    // --- language question sets language ---
    @Test fun languageQuestionParsesTagalog() {
        val q = Onboarding.QUESTIONS.first { it.id == "language" }
        assertEquals(Lang.TL, q.apply(UserProfile(), "Tagalog").preferredLanguage)
        assertEquals(Lang.EN, q.apply(UserProfile(), "English").preferredLanguage)
    }

    // --- full fold produces a usable profile ---
    @Test fun fullOnboardingFoldBuildsProfile() {
        var p = UserProfile()
        val answers = listOf(
            "Tagalog", "Juan", "O+", "penicillin", "asthma",
            "Maria", "+639171234567", "San Pablo City", "yes", "no",
        )
        answers.forEachIndexed { i, a -> p = Onboarding.applyAnswer(p, i, a) }
        assertEquals(Lang.TL, p.preferredLanguage)
        assertEquals("Juan", p.name)
        assertEquals("O+", p.bloodType)
        assertEquals(listOf("penicillin"), p.allergies)
        assertEquals("Maria", p.emergencyContactName)
        assertEquals("+639171234567", p.emergencyContactNumber)
        assertEquals("San Pablo City", p.home)
        assertTrue(p.householdInfant)
        assertFalse(p.householdElderly)
        assertTrue(p.hasEmergencyContact)
    }

    @Test fun skipLeavesFieldUnchanged() {
        val p = Onboarding.applyAnswer(UserProfile(name = "Keep"), 1, "skip")
        assertEquals("Keep", p.name)
    }
}
