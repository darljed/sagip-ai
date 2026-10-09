package dev.darl.sagip.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RepetitionGuardTest {

    @Test fun catchesDashILoop() {
        val loop = "4. Itigil ang malubhang nasaktan at mag" + "-i".repeat(60)
        assertTrue(looksRepetitive(loop))
    }

    @Test fun catchesSingleCharRun() {
        assertTrue(looksRepetitive("step one is to " + "a".repeat(60)))
    }

    @Test fun allowsNormalProse() {
        val normal = "1. Apply firm pressure on the wound with a clean cloth. " +
            "2. Keep pressing continuously and raise the limb above the heart. " +
            "3. Lay the person down and keep them warm to prevent shock."
        assertFalse(looksRepetitive(normal))
    }

    @Test fun allowsNormalTagalog() {
        val tl = "1. Diinan nang mabuti ang sugat gamit ang malinis na tela. " +
            "2. Itaas ang nasugatan na bahagi nang mas mataas sa puso. " +
            "3. Ihiga ang tao at panatilihing mainit ang katawan."
        assertFalse(looksRepetitive(tl))
    }

    @Test fun shortTextNeverFlagged() {
        assertFalse(looksRepetitive("1. Apply pressure"))
    }

    @Test fun trimKeepsGoodPrefix_dropsLoop() {
        val looped = "1. Diinan ang sugat. 2. Itaas sa puso. 3. Ihiga ang tao" + "-i".repeat(40)
        val trimmed = trimRepetitionTail(looped)
        assertTrue("keeps the real steps", trimmed.contains("Itaas sa puso"))
        assertFalse("drops the loop", trimmed.contains("-i-i-i-i-i"))
    }

    @Test fun trimLeavesCleanTextAlone() {
        val clean = "1. Apply pressure. 2. Raise the limb. 3. Keep warm."
        assertEquals(clean.trimEnd(), trimRepetitionTail(clean))
    }
}
