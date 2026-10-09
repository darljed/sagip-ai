package dev.darl.sagip.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class IllustrationIndexTest {
    private val dir = File("src/main/assets/illustrations")
    private val index = IllustrationIndex.parse(File(dir, "index.json").readText())
    private val topicFiles = File(dir, "topics").list()?.toSet() ?: emptySet()
    private val chunks = (File("src/main/assets/packs").listFiles { f -> f.name.endsWith(".json") } ?: emptyArray())
        .flatMap { PackRepository.parsePack(it.readText()) }
    private val repo = TopicRepository(PackRepository(chunks))

    @Test fun everyTopicHasAHero() {
        val missing = repo.topics.map { it.key }.filter { k -> topicFiles.none { it.startsWith("$k.hero.") } }
        assertTrue("topics without hero art: $missing", missing.isEmpty())
    }

    @Test fun everyCategoryHasACoverFromAnExistingHero() {
        Categories.present(repo.topics).forEach { c ->
            val t = index.covers[c.id]
            assertTrue("category ${c.id} has no cover mapping", t != null)
            assertTrue("cover topic $t for ${c.id} has no hero", topicFiles.any { it.startsWith("$t.hero.") })
        }
    }

    @Test fun stepMappingsPointAtRealFilesAndRealSteps() {
        index.steps.forEach { (topic, slots) ->
            val t = repo.topics.firstOrNull { it.key == topic }
            assertTrue("unknown topic $topic", t != null)
            slots.forEach { (slot, n) ->
                assertTrue("missing file $topic.$slot.*", topicFiles.any { it.startsWith("$topic.$slot.") })
                assertTrue("step $n out of range for $topic", n in 1..t!!.steps(Lang.EN).size)
                assertEquals("EN/TL step counts must match for $topic", t.steps(Lang.EN).size, t.steps(Lang.TL).size)
            }
        }
    }

    @Test fun everyExtraImageIsMappedToAStep() {
        val extras = topicFiles.filter { !it.contains(".hero.") }
        val mapped = index.steps.flatMap { (t, s) -> s.keys.map { "$t.$it." } }
        val unmapped = extras.filter { f -> mapped.none { f.startsWith(it) } }
        assertTrue("extras not mapped to a step (they will show as reference images): $unmapped", unmapped.isEmpty())
    }
}
