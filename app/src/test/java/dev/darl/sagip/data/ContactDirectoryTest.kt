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

    @Test fun gpsCoordinatesResolveAreaOffline() {
        assertEquals("makati", dir.placeFor("geo:14.5600,121.0300")?.area?.id)
        assertEquals("san_pablo", dir.placeFor("geo:14.0700,121.3300")?.area?.id)
        assertNull(dir.placeFor("geo:10.3157,123.8854")) // Cebu — no data, no wrong guess
    }

    @Test fun addressPlusGeoRefinesToBarangay() {
        val p = dir.placeFor("Poblacion geo:14.5600,121.0300")
        assertEquals("makati", p?.area?.id); assertEquals("Poblacion", p?.barangay?.name)
    }

    @Test fun searchFindsAreasAndBarangays() {
        assertTrue(dir.search("makat").any { it.area.id == "makati" })
        assertTrue(dir.search("ignacio").any { it.barangay?.name == "San Ignacio" })
        assertTrue(dir.search("zzzz").isEmpty())
    }

    @Test fun placeLabelDropsGeoToken() {
        assertEquals("Poblacion, Makati", placeLabel("Poblacion, Makati geo:14.5600,121.0300"))
        assertEquals("", placeLabel("geo:14.5600,121.0300"))
    }
}
