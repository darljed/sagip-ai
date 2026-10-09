package dev.darl.sagip.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Phase 2 self-check — runs on the JVM, no device needed.
 * Parses the REAL bundled pack files with the production parser and asserts the
 * invariants we rely on downstream (retrieval, personalization, UI).
 *
 * Run: ./gradlew :app:testDebugUnitTest --tests "*PackRepositoryTest*"
 */
class PackRepositoryTest {

    private val assetsDir = File("src/main/assets")

    private fun loadAll(): List<Chunk> =
        PackRepository.PACK_FILES.flatMap { rel ->
            val f = File(assetsDir, rel)
            assertTrue("pack file missing: ${f.path}", f.exists())
            PackRepository.parsePack(f.readText())
        }

    @Test fun allChunksLoad_andCorpusIsNonTrivial() {
        val chunks = loadAll()
        // Count is intentionally NOT hardcoded — packs grow. Assert a sane floor and
        // that every topic ships as an EN+TL pair (so the total is even).
        assertTrue("expected a non-trivial corpus, got ${chunks.size}", chunks.size >= 32)
        assertEquals("corpus must be EN/TL paired (even count)", 0, chunks.size % 2)
    }

    @Test fun enTlBalanceIsEqual() {
        val chunks = loadAll()
        val en = chunks.count { it.lang == "en" }
        val tl = chunks.count { it.lang == "tl" }
        assertEquals("EN and TL chunk counts must match", en, tl)
    }

    @Test fun everyEnChunkHasTlTwin_sameIdStem() {
        val chunks = loadAll()
        val en = chunks.filter { it.lang == "en" }.map { it.id }.toSet()
        val tl = chunks.filter { it.lang == "tl" }.map { it.id }.toSet()
        assertEquals("EN and TL ids must mirror 1:1", en, tl)
    }

    @Test fun requiredFieldsPresent_andSourcesNamed() {
        val chunks = loadAll()
        chunks.forEach { c ->
            assertTrue("id blank", c.id.isNotBlank())
            assertTrue("title blank for ${c.id}", c.title.isNotBlank())
            assertTrue("text blank for ${c.id}", c.text.isNotBlank())
            assertTrue("source blank for ${c.id}", c.source.isNotBlank())
            assertTrue("tags empty for ${c.id}", c.tags.isNotEmpty())
        }
    }

    @Test fun corePacksPresent() {
        val repo = PackRepository(loadAll())
        // The three originals must always be present; extra packs (fire, volcano, …)
        // are welcome and must not break this.
        assertTrue(repo.packNames.containsAll(setOf("first_aid", "typhoon_flood", "earthquake")))
    }

    @Test fun criticalChunksCallEmergency() {
        // Trust-layer invariant: anything life-threatening should direct to help.
        val chunks = loadAll()
        val criticalNotCalling = chunks
            .filter { it.severity == Severity.CRITICAL && !it.callEmergency }
            .map { it.id }
        assertTrue(
            "critical chunks must set call_emergency=true: $criticalNotCalling",
            criticalNotCalling.isEmpty()
        )
    }

    @Test fun tagsAreBilingual_haveTagalogKeywords() {
        // Spot-check: severe bleeding must be findable by the Tagalog word "dugo".
        val chunks = loadAll()
        val bleeding = chunks.first { it.topic == "severe_bleeding" && it.lang == "en" }
        assertTrue("expected Tagalog keyword in tags", bleeding.tags.any { it == "dugo" })
    }
}
