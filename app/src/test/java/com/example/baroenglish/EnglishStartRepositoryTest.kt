package com.example.baroenglish

import org.junit.Assert.*
import org.junit.Test

class EnglishStartRepositoryTest {
    private fun row(start: String = "3.984", duration: String = "17") =
        """{"schemaVersion":1,"videos":{"4NtiP8Kn9qE":{"status":"ready","englishStartSeconds":$start,"durationSeconds":$duration},"W8m60oVo8Jk":{"status":"pending"}}}"""
    @Test fun knownFractionalTimeAndUnknownAreDistinct() {
        val data = EnglishStartRepository.parse(row())
        assertEquals(3.984, data["4NtiP8Kn9qE"]!!, 0.00001)
        assertNull(data["W8m60oVo8Jk"])
    }
    @Test fun zeroMeansAlreadyEnglish() {
        assertEquals(0.0, EnglishStartRepository.parse(row("0"))["4NtiP8Kn9qE"]!!, 0.0)
    }
    @Test fun unsafeTimesRejectWholeUpdate() {
        for (start in listOf("-1", "17", "18", "1e309", "null", "\"NaN\"")) {
            assertTrue("Rejected $start", runCatching { EnglishStartRepository.parse(row(start)) }.isFailure)
        }
    }
    @Test fun wrongSchemaInvalidIdAndHtmlAreRejected() {
        for (text in listOf(row().replace("schemaVersion\":1", "schemaVersion\":2"),
            row().replace("4NtiP8Kn9qE", "bad-id"), "<html>proxy failure</html>")) {
            assertTrue(runCatching { EnglishStartRepository.parse(text) }.isFailure)
        }
    }
    @Test fun emptyCatalogCanRevokeAllTimings() {
        assertTrue(EnglishStartRepository.parse("""{"schemaVersion":1,"videos":{}}""").isEmpty())
    }
}
