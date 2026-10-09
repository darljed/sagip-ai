package dev.darl.sagip.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class MentionedContactsTest {
    private val baby = ContactChip("Baby", "09062457566")
    private val cdrrmo = ContactChip("San Pablo CDRRMO", "(049) 555-0101", sample = true)

    @Test fun noMention_noChips() =
        assertEquals(emptyList<ContactChip>(), ChatViewModel.mentionedContacts("Diinan nang malakas. Tumawag sa 911.", listOf(baby, cdrrmo)))

    @Test fun nameMention_givesChip() =
        assertEquals(listOf(baby), ChatViewModel.mentionedContacts("Tawagan si baby kapag may signal.", listOf(baby, cdrrmo)))

    @Test fun numberMention_givesChip() =
        assertEquals(listOf(cdrrmo), ChatViewModel.mentionedContacts("Call (049) 555-0101 now.", listOf(baby, cdrrmo)))
}
