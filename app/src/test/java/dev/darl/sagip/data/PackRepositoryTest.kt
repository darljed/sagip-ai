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

    @Test fun allChunksLoad_andCountIs32() {
        val chunks = loadAll()
        assertEquals("expected 32 total chunks", 32, chunks.size)
    }

    @Test fun enTlBalanceIsEqual() {
        val chunks = loadAll()
        val en = chunks.count { it.lang == "en" }
        val tl = chunks.count { it.lang == "tl" }
        assertEquals("EN count", 16, en)
        assertEquals("TL count", 16, tl)
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

    @Test fun threePacksPresent() {
        val repo = PackRepository(loadAll())
        assertEquals(setOf("first_aid", "typhoon_flood", "earthquake"), repo.packNames)
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
