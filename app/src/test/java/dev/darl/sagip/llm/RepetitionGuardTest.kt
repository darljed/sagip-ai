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

    @Test fun catchesRepeatedPhrase() {
        // The real 1B failure: a clause repeated several times.
        val loop = "Diinan ang sugat gamit ang tela. ".repeat(4) + "Tapos itaas."
        assertTrue(looksRepetitive(loop))
        assertTrue(looksPhraseRepetitive(loop))
    }

    @Test fun catchesLowUniqueWordRatio() {
        val loop = ("mag ingat mag ingat mag ingat mag ingat mag ingat mag ingat")
        assertTrue(looksPhraseRepetitive(loop))
    }

    @Test fun phraseGuardAllowsDiverseProse() {
        val tl = "Mahalagang pag-aalala! May matinding pagdurugo ang taong ito. " +
            "Manatiling kalmado at kumilos agad habang hinihintay ang tulong."
        assertFalse(looksPhraseRepetitive(tl))
    }

    @Test fun trimDropsDuplicateSentences() {
        val doubled = "Itaas ang katawan. Itaas ang katawan. Panatilihing mainit."
        val trimmed = trimRepetitionTail(doubled)
        // Only one copy of the duplicated sentence should remain.
        assertEquals(1, Regex("Itaas ang katawan").findAll(trimmed).count())
    }

    @Test fun stripsControlTokens() {
        assertEquals("", stripControlTokens("<end_of_turn><end_of_turn><end_of_turn>"))
        assertEquals("Hello", stripControlTokens("<start_of_turn>model\nHello<end_of_turn>").removePrefix("model").trim())
    }

    @Test fun cleanAnswerCutsFakeSecondTurn() {
        val raw = "Kalmado lang po. Narito ang gagawin:\n1. Humiga.\n2. Magpahinga.\nResponse:\nOkay, uulitin ko"
        val c = cleanAnswer(raw)
        assertFalse(c.contains("Response"))
        assertTrue(c.contains("2. Magpahinga."))
    }
}
