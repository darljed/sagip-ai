package dev.darl.sagip.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ContactDirectoryTest {
    private val dir = ContactDirectory.parse(File("src/main/assets/contacts.json").readText())

    @Test fun nationalHas911() = assertEquals("911", dir.national.first { it.kind == "emergency" }.number)

    @Test fun makatiDetectedFromHomeText() {
        val p = dir.placeFor("Brgy. Poblacion, Makati City")
        assertEquals("makati", p?.area?.id); assertEquals("Poblacion", p?.barangay?.name)
    }

    @Test fun unknownAreaGivesNoPlace() = assertNull(dir.placeFor("Cebu City"))

    @Test fun localContactsOnlyWithNumbers_barangayFirst() {
        val l = dir.localContacts(dir.placeFor("Poblacion Makati"))
        assertTrue(l.isNotEmpty() && l.all { it.number != null })
        assertEquals("barangay_hall", l.first().kind)
    }

    @Test fun findFireIncludesLocalFireAnd911() {
        val f = dir.find(setOf("fire", "emergency"), dir.placeFor("Makati"))
        assertTrue(f.any { it.kind == "fire" }); assertTrue(f.any { it.number == "911" })
        assertNotNull(f.firstOrNull { it.sample })
    }
}
