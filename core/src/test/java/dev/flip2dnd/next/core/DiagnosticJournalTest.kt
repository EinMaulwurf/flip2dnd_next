package dev.flip2dnd.next.core

import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DiagnosticJournalTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun read(journal: DiagnosticJournal) = ByteArrayOutputStream().also { journal.writeTo(it) }
        .toString("UTF-8")

    @Test fun rotationRetainsTheNewestRecordsInChronologicalOrderWithinTheByteLimit() {
        val directory = temporary.newFolder()
        val journal = DiagnosticJournal(directory, bytesPerFile = 10, fileCount = 3)
        repeat(20) { assertTrue(journal.append("event-$it")) }
        assertEquals("event-17\nevent-18\nevent-19\n", read(journal))
        assertTrue(directory.listFiles()!!.sumOf { it.length() } <= 30)
        assertEquals(3, directory.listFiles()!!.size)
    }

    @Test fun recreationPreservesEarlierRecordsAndContinuesRotation() {
        val directory = temporary.newFolder()
        DiagnosticJournal(directory, 8, 2).apply { append("first"); append("second") }
        val recreated = DiagnosticJournal(directory, 8, 2)
        assertEquals("first\nsecond\n", read(recreated))
        recreated.append("third")
        assertEquals("second\nthird\n", read(recreated))
    }

    @Test fun limitsCountUtf8BytesRatherThanCharacters() {
        val directory = temporary.newFolder()
        val journal = DiagnosticJournal(directory, 6, 2)
        repeat(3) { journal.append("éé") }
        assertEquals("éé\néé\n", read(journal))
        assertTrue(directory.listFiles()!!.sumOf { it.length() } <= 12)
    }

    @Test fun oversizedRecordsCannotExceedTheLimitOrDestroyExistingData() {
        val journal = DiagnosticJournal(temporary.newFolder(), 12, 2)
        journal.append("small")
        assertFalse(journal.append("x".repeat(100)))
        assertEquals("small\n", read(journal))
    }

    @Test fun recreationDiscardsAPartialRecordFromAnInterruptedWrite() {
        val directory = temporary.newFolder()
        java.io.File(directory, "events.0.jsonl").writeText("complete\ninterrupted")
        val journal = DiagnosticJournal(directory)
        journal.append("next")
        assertEquals("complete\nnext\n", read(journal))
    }

    @Test fun clearingLeavesAnEmptyJournalThatCanBeUsedAgain() {
        val journal = DiagnosticJournal(temporary.newFolder(), 10, 2)
        journal.append("first")
        journal.append("second")
        journal.clear()
        assertEquals("", read(journal))
        journal.append("new")
        assertEquals("new\n", read(journal))
    }
}
