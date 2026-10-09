package dev.darl.sagip.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TopicRepositoryTest {
    private val assets = File("src/main/assets")
    private val chunks = (File(assets, "packs").listFiles { f -> f.name.endsWith(".json") } ?: emptyArray())
        .flatMap { PackRepository.parsePack(it.readText()) }
    private val repo = TopicRepository(PackRepository(chunks))

    @Test fun everyTopicHasBothLanguagesAndSteps() {
        assertTrue(repo.topics.size >= 40)
        repo.topics.forEach { t ->
            assertNotNull("${t.id} en", t.en); assertNotNull("${t.id} tl", t.tl)
            assertTrue("${t.id} steps", t.steps(Lang.TL).isNotEmpty() && t.steps(Lang.EN).isNotEmpty())
        }
    }

    @Test fun stepsAreParsedWithoutNumbers() {
        assertEquals(listOf("Do x", "Do y"), Topic.parseSteps("1. Do x\n2. Do y"))
    }

    @Test fun searchFindsTagalogAffixedWord() {
        val hits = repo.search("lumilindol").map { it.key }
        assertTrue("earthquake topic expected in $hits", hits.any { it.contains("earthquake") })
    }

    @Test fun everyTopicBelongsToACategoryThatRenders() {
        val present = Categories.present(repo.topics).map { it.id }.toSet()
        repo.topics.forEach { assertTrue("${it.id} category ${it.category}", it.category in present) }
    }
}
