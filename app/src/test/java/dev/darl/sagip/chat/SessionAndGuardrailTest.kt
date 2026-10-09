package dev.darl.sagip.chat

import dev.darl.sagip.data.PromptBuilder
import dev.darl.sagip.data.Severity
import dev.darl.sagip.data.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionAndGuardrailTest {
    @Test fun sessionRoundTrip_keepsRelatedAndContacts() {
        val s = ChatSession(
            "id1", "nakagat ako ng ahas", 123L,
            listOf(
                Message(Role.USER, "nakagat ako ng ahas"),
                Message(Role.ASSISTANT, "**Kalma**\n1. Huwag gumalaw", Severity.CRITICAL,
                    listOf(RelatedGuide("first_aid:snakebite", "Kagat ng ahas", Severity.CRITICAL)),
                    listOf(ContactChip("Mama", "0917", false), ContactChip("Makati Fire", "(02) 8555-0102", true))),
            ),
        )
        val back = SessionStore.parse(SessionStore.toJson(listOf(s))).single()
        assertEquals(s.title, back.title)
        assertEquals(2, back.messages.size)
        assertEquals("first_aid:snakebite", back.messages[1].related.single().topicId)
        assertTrue(back.messages[1].contacts[1].sample)
        assertEquals(Severity.CRITICAL, back.messages[1].severity)
    }

    @Test fun streamingMessagesAreNotPersisted() {
        val s = ChatSession("i", "t", 1L, listOf(Message(Role.USER, "q"), Message(Role.ASSISTANT, "partial", streaming = true)))
        assertEquals(1, SessionStore.parse(SessionStore.toJson(listOf(s))).single().messages.size)
    }

    @Test fun offTopicSentinelDetected() {
        assertTrue(ChatViewModel.isOffTopic("OFF_TOPIC"))
        assertTrue(ChatViewModel.isOffTopic("  off topic "))
        assertFalse(ChatViewModel.isOffTopic("1. Diinan ang sugat. Hindi ito off topic para sa first aid na ito ay mahalaga."))
    }

    @Test fun streamingHoldsBackPossibleSentinel() {
        assertTrue(ChatViewModel.couldBeOffTopic("OFF"))
        assertTrue(ChatViewModel.couldBeOffTopic("OFF_TOP"))
        assertFalse(ChatViewModel.couldBeOffTopic("Kalma lang"))
    }

    @Test fun promptCarriesScopeGate() {
        val p = PromptBuilder.build("candy please", UserProfile(), emptyList())
        assertTrue(p.contains("OFF_TOPIC"))
        assertTrue(p.contains("SCOPE"))
    }
}
