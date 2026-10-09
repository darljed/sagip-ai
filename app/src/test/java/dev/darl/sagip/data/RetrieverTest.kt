package dev.darl.sagip.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Phase 3-prep self-check — CAG retrieval over the real packs, no model needed.
 * Run: ./gradlew :app:testDebugUnitTest --tests "*RetrieverTest*"
 */
class RetrieverTest {

    private val repo: PackRepository by lazy {
        val assetsDir = File("src/main/assets")
        val chunks = PackRepository.PACK_FILES.flatMap {
            PackRepository.parsePack(File(assetsDir, it).readText())
        }
        PackRepository(chunks)
    }
    private val retriever by lazy { KeywordRetriever(repo) }

    @Test fun englishBleedingQuery_returnsBleedingChunk_inEnglish() {
        val hits = retriever.retrieve("someone is bleeding heavily and dizzy", Lang.EN)
        assertTrue("expected at least one hit", hits.isNotEmpty())
        assertEquals("severe_bleeding", hits.first().topic)
        assertEquals("en", hits.first().lang)
    }

    @Test fun tagalogFloodQuery_returnsFloodChunk_inTagalog() {
        // "may baha na papasok sa bahay" = flood entering the house
        val hits = retriever.retrieve("may baha na papasok sa bahay", Lang.TL)
        assertTrue(hits.isNotEmpty())
        assertEquals("flood_entering_home", hits.first().topic)
        assertEquals("tl", hits.first().lang) // language resolved to the twin
    }

    @Test fun tagalogKeyword_matchesEnglishTaggedChunk_resolvesToTagalog() {
        // Query uses Tagalog "lindol" (earthquake); must still find the topic and
        // return the Tagalog twin.
        val hits = retriever.retrieve("lindol", Lang.TL)
        assertTrue(hits.isNotEmpty())
        assertTrue(hits.any { it.pack == "earthquake" })
        assertTrue("all returned chunks should be TL", hits.all { it.lang == "tl" })
    }

    @Test fun choking_isFound() {
        val hits = retriever.retrieve("he is choking and cannot breathe", Lang.EN)
        assertEquals("choking_adult", hits.first().topic)
    }

    @Test fun snakebite_tagalog_isFound() {
        val hits = retriever.retrieve("kinagat ng ahas", Lang.TL)
        assertEquals("snakebite", hits.first().topic)
    }

    @Test fun topicsAreDeduped_oneChunkPerTopic() {
        val hits = retriever.retrieve("bleeding blood dugo sugat", Lang.EN, k = 4)
        val topics = hits.map { it.topic }
        assertEquals("no duplicate topics", topics.size, topics.toSet().size)
    }

    @Test fun gibberishQuery_returnsEmpty_notCrash() {
        val hits = retriever.retrieve("zzxqwkjf qppz", Lang.EN)
        assertTrue("unknown query returns empty", hits.isEmpty())
    }

    @Test fun respectsK() {
        val hits = retriever.retrieve("earthquake fire flood bleeding water gas", Lang.EN, k = 2)
        assertTrue("should return at most k", hits.size <= 2)
    }
}
