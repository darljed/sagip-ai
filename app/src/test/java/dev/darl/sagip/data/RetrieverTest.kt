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

    @Test fun strongSingleTopicQuery_doesNotDragLooseSecondTopic() {
        // "severe bleeding" strongly matches severe_bleeding; it must NOT also pull in
        // fracture (which shares body words like "pressure"/"wound").
        val hits = retriever.retrieve("severe bleeding", Lang.EN, k = 3)
        assertEquals("should return bleeding alone", listOf("severe_bleeding"), hits.map { it.topic })
    }

    @Test fun genuinelyMultiTopicQuery_stillReturnsMultiple() {
        // Explicitly naming two strong topics should keep both.
        val hits = retriever.retrieve("bleeding and choking", Lang.EN, k = 3)
        val topics = hits.map { it.topic }.toSet()
        assertTrue("bleeding kept", "severe_bleeding" in topics)
        assertTrue("choking kept", "choking_adult" in topics)
    }

    @Test fun respectsK() {
        val hits = retriever.retrieve("earthquake fire flood bleeding water gas", Lang.EN, k = 2)
        assertTrue("should return at most k", hits.size <= 2)
    }

    @Test fun tagalogConjugation_lumilindol_matchesEarthquake() {
        // Real demo miss: "lumilindol" shares the stem "lindol" with the earthquake
        // tag but is not a whole-word match. It must NOT fall through to flood.
        val hits = retriever.retrieve("lumilindol anong gagawin ko", Lang.TL)
        assertEquals("during_earthquake", hits.first().topic)
    }

    @Test fun tagalogConjugation_makuryente_matchesElectrical() {
        // Several electrical guides tie on score here; the downed-line guide must be among them
        // (which one leads depends on tie-breaks, not on this stemming behaviour).
        val hits = retriever.retrieve("may nabubag na poste ng kuryente baka makuryente", Lang.TL, k = 4)
        assertTrue(hits.map { it.topic }.toString(), hits.any { it.topic == "electrical_hazard" })
    }

    @Test fun unmatchedTopic_returnsEmpty_notWrongPack() {
        // "falling tree" has NO pack. It must return empty (→ 911 fallback), NOT a
        // weak spurious match (previously grabbed the flood pack via "puno"/"dapat").
        val hits = retriever.retrieve("ah may bumabagsak na puno anong dapat gawin", Lang.TL)
        assertTrue("no genuine match returns empty", hits.isEmpty())
    }

    @Test fun weakSingleWordOverlap_doesNotPassFloor() {
        // A query that only grazes a body word must not surface a pack.
        val hits = retriever.retrieve("puno dapat", Lang.TL)
        assertTrue("below score floor returns empty", hits.isEmpty())
    }

    @Test fun snakeSighting_notBitten_routesToEncounter_notSnakebite() {
        val hits = retriever.retrieve("hindi ako nakagat ng ahas nakakita lang ako", Lang.TL)
        // Keyword retrieval can't read negation, so both snake topics may surface; the
        // sighting topic must rank FIRST and the LLM (grounded prompt) picks the fit.
        assertEquals("snake_encounter", hits.first().topic)
    }

    @Test fun snakeSighting_english() {
        val hits = retriever.retrieve("I saw a snake what should I do", Lang.EN)
        assertEquals("snake_encounter", hits.first().topic)
    }

    @Test fun snakeBite_ranksSnakebiteFirst() {
        for (q in listOf("kinagat ako ng ahas", "nakagat ako ng ahas", "snake bit me", "snakebite")) {
            val hits = retriever.retrieve(q, Lang.TL)
            assertEquals("query: $q", "snakebite", hits.first().topic)
        }
    }

    @Test fun fainting_doesNotMatchEyeInjury_viaMidWordSubstring() {
        // "nahimatay" contains "mata" mid-word; must not route to the eye-injury chunk.
        val hits = retriever.retrieve("may nahimatay", Lang.TL)
        assertTrue("eye chunk must not match", hits.none { it.topic.contains("eye") })
    }

    @Test fun nakuryente_leadsWithElectricShock_inBothLanguages() {
        for (lang in listOf(Lang.TL, Lang.EN)) {
            val hits = retriever.retrieve("may nakuryente", lang, k = 3)
            assertEquals("lang $lang", "electric_shock", hits.first().topic)
        }
    }
}
