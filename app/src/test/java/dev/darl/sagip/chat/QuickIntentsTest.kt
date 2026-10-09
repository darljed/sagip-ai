package dev.darl.sagip.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickIntentsTest {
    @Test fun fireNumber() {
        val i = QuickIntents.detect("ano ang number ng bumbero") as QuickIntent.FindContacts
        assertTrue("fire" in i.kinds)
    }
    @Test fun hospitalNumber() {
        val i = QuickIntents.detect("hospital hotline please") as QuickIntent.FindContacts
        assertTrue("hospital" in i.kinds)
    }
    @Test fun barangayHall() {
        val i = QuickIntents.detect("numero ng barangay hall") as QuickIntent.FindContacts
        assertTrue("barangay_hall" in i.kinds)
    }
    @Test fun callMomAndWife() {
        assertEquals(QuickIntent.CallFamily, QuickIntents.detect("call my mom"))
        assertEquals(QuickIntent.CallFamily, QuickIntents.detect("tawagan mo si asawa ko"))
        assertEquals(QuickIntent.CallFamily, QuickIntents.detect("I want to call my wife"))
        assertEquals(QuickIntent.CallFamily, QuickIntents.detect("tawagan ang nanay ko"))
    }
    @Test fun normalQuestionsAreNotIntercepted() {
        assertNull(QuickIntents.detect("nakagat ako ng ahas"))
        assertNull(QuickIntents.detect("may sunog sa bahay namin"))
        assertNull(QuickIntents.detect("matinding pagdurugo"))
        assertNull(QuickIntents.detect("my mom is choking"))
    }
}
