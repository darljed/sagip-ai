package dev.darl.sagip.onboarding

import dev.darl.sagip.data.Lang
import dev.darl.sagip.data.ProfileStore
import dev.darl.sagip.data.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WizardTest {

    @Test fun profileRoundTripsWithBirthday() {
        val p = UserProfile.DEMO
        val restored = ProfileStore.fromJson(ProfileStore.toJson(p).toString())
        assertEquals(p, restored)
        assertEquals("1990-06-15", restored.birthday)
    }

    @Test fun requiredStepsBlockAdvance_optionalDoNot() {
        val lang = Wizard.STEPS.indexOfFirst { it.id == "language" }
        val name = Wizard.STEPS.indexOfFirst { it.id == "name" }
        val blood = Wizard.STEPS.indexOfFirst { it.id == "blood_type" }
        assertFalse("blank required language invalid", Wizard.isValid(lang, ""))
        assertTrue("filled language valid", Wizard.isValid(lang, "English"))
        assertFalse("blank required name invalid", Wizard.isValid(name, ""))
        assertTrue("optional blood blank is fine", Wizard.isValid(blood, ""))
    }

    @Test fun languageStepSetsLang() {
        val i = Wizard.STEPS.indexOfFirst { it.id == "language" }
        assertEquals(Lang.TL, Wizard.applyStep(UserProfile(), i, "Tagalog").preferredLanguage)
        assertEquals(Lang.EN, Wizard.applyStep(UserProfile(), i, "English").preferredLanguage)
    }

    @Test fun multiChipAllergiesParsed_noneDropped() {
        val i = Wizard.STEPS.indexOfFirst { it.id == "allergies" }
        assertEquals(listOf("Penicillin", "Seafood"), Wizard.applyStep(UserProfile(), i, "Penicillin, Seafood").allergies)
        assertEquals(emptyList<String>(), Wizard.applyStep(UserProfile(), i, "None").allergies)
    }

    @Test fun householdChipsMapToFlags() {
        val i = Wizard.STEPS.indexOfFirst { it.id == "household" }
        val p = Wizard.applyStep(UserProfile(), i, "Baby or toddler, Elderly parent / grandparent")
        assertTrue(p.householdInfant)
        assertTrue(p.householdElderly)
        assertFalse(p.householdPwd)
    }

    @Test fun contactStepSplitsNameAndNumber() {
        val i = Wizard.STEPS.indexOfFirst { it.id == "contact" }
        val p = Wizard.applyStep(UserProfile(), i, "Camille|+63 906 245 7566")
        assertEquals("Camille", p.emergencyContactName)
        assertEquals("+639062457566", p.emergencyContactNumber)
        assertTrue(p.hasEmergencyContact)
    }

    @Test fun noSeparateContactNameStep() {
        assertFalse(Wizard.STEPS.any { it.id == "contact_name" })
        assertFalse(Wizard.STEPS.any { it.id == "contact_number" })
    }

    @Test fun multiSelectStepsAllowCustom() {
        listOf("allergies", "conditions", "household").forEach { id ->
            val step = Wizard.STEPS.first { it.id == id }
            assertTrue("$id should allow custom text", step.allowCustom)
        }
    }

    @Test fun reviewStepIsLast() {
        assertEquals("review", Wizard.STEPS.last().id)
    }

    @Test fun bloodTypeUnknownClears() {
        val i = Wizard.STEPS.indexOfFirst { it.id == "blood_type" }
        assertEquals("", Wizard.applyStep(UserProfile(), i, "Unknown").bloodType)
        assertEquals("O+", Wizard.applyStep(UserProfile(), i, "O+").bloodType)
    }

    // --- age + comprehension ---
    @Test fun ageComputedFromBirthday() {
        val yr = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        val tenYearsAgo = "${yr - 10}-01-01"
        assertEquals(10, UserProfile(birthday = tenYearsAgo).age)
    }

    @Test fun ageNullWhenNoBirthday() {
        assertNull(UserProfile().age)
    }

    @Test fun comprehensionHintForChild() {
        val yr = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        val child = UserProfile(birthday = "${yr - 8}-01-01")
        assertTrue(child.comprehensionHint()!!.contains("child"))
    }

    @Test fun comprehensionHintNullForAdult() {
        val yr = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        val adult = UserProfile(birthday = "${yr - 30}-01-01")
        assertNull(adult.comprehensionHint())
    }

    @Test fun wizardHasExpectedSteps() {
        val ids = Wizard.STEPS.map { it.id }
        assertTrue(ids.contains("language"))
        assertTrue(ids.contains("birthday"))
        assertTrue(ids.contains("household"))
        assertEquals("language asked first", "language", ids.first())
    }
}
