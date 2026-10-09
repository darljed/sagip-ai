package dev.darl.sagip.chat

import dev.darl.sagip.data.KeywordRetriever
import dev.darl.sagip.data.Lang
import dev.darl.sagip.data.PackRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FollowUpTest {
    private val retriever by lazy {
        val dir = File("src/main/assets")
        KeywordRetriever(PackRepository(PackRepository.PACK_FILES.flatMap { PackRepository.parsePack(File(dir, it).readText()) }))
    }

    @Test fun detectsFollowUps() {
        assertTrue(FollowUp.looksLike("paano kung walang response?"))
        assertTrue(FollowUp.looksLike("dapat ba na mabilis?"))
        assertTrue(FollowUp.looksLike("what if it's a child?"))
        assertTrue(FollowUp.looksLike("how long should I keep going"))
        assertFalse(FollowUp.looksLike("may baha na papasok sa bahay"))
        assertFalse(FollowUp.looksLike("someone is bleeding heavily and dizzy"))
    }

    @Test fun contextQueryWalksBackToTheAnchorQuestion() {
        val q = FollowUp.contextQuery(listOf("Paano mag-CPR", "dapat ba na mabilis?"), "paano kung walang response?")!!
        assertTrue(q.contains("CPR")); assertTrue(q.contains("walang response"))
        assertEquals(null, FollowUp.contextQuery(emptyList(), "paano kung walang response?"))
    }

    @Test fun cprFollowUpStaysOnCpr_notNoSignal() {
        val alone = retriever.retrieve("paano kung walang response?", Lang.TL, 3)
        val q = FollowUp.contextQuery(listOf("Paano mag-CPR", "dapat ba na mabilis?"), "paano kung walang response?")!!
        val withContext = retriever.retrieve(q, Lang.TL, 3)
        println("alone=" + alone.map { it.topic } + " withContext=" + withContext.map { it.topic })
        assertTrue("expected a CPR topic first, got ${withContext.map { it.topic }}", withContext.first().topic.startsWith("cpr"))
    }
}
