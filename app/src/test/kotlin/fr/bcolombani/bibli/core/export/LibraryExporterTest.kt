package fr.bcolombani.bibli.core.export

import fr.bcolombani.bibli.core.metadata.BookSource
import fr.bcolombani.bibli.core.model.Book
import java.time.ZoneOffset
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryExporterTest {

    private val book = Book(
        id = 1,
        isbn13 = "9782070368228",
        rawScan = "9782070368228",
        title = "L'Étranger",
        authors = "Albert Camus",
        source = BookSource.GOOGLE_BOOKS,
        addedAt = 1_756_562_291_000L, // 2025-08-30T13:58:11Z
    )

    @Test
    fun `le JSON respecte le schema documente`() {
        val exportedAt = 1_756_562_400_000L
        val json = Json.parseToJsonElement(LibraryExporter.toJson(listOf(book), exportedAt)).jsonObject

        assertEquals(1, json["schemaVersion"]?.jsonPrimitive?.content?.toInt())
        assertEquals(1, json["count"]?.jsonPrimitive?.content?.toInt())
        assertEquals(
            LibraryExporter.formatInstant(exportedAt),
            json["exportedAt"]?.jsonPrimitive?.content,
        )

        val entry = json["books"]!!.jsonArray.single() as JsonObject
        assertEquals("9782070368228", entry["isbn13"]?.jsonPrimitive?.content)
        assertEquals("L'Étranger", entry["title"]?.jsonPrimitive?.content)
        assertEquals("Albert Camus", entry["authors"]?.jsonPrimitive?.content)
        assertEquals("GOOGLE_BOOKS", entry["source"]?.jsonPrimitive?.content)
        assertEquals("2025-08-30T13:58:11Z", entry["addedAt"]?.jsonPrimitive?.content)
        assertEquals(setOf("isbn13", "title", "authors", "source", "addedAt"), entry.keys)
    }

    @Test
    fun `bibliotheque vide`() {
        val json = Json.parseToJsonElement(LibraryExporter.toJson(emptyList(), 0L)).jsonObject
        assertEquals(0, json["count"]?.jsonPrimitive?.content?.toInt())
        assertEquals(0, json["books"]!!.jsonArray.size)
    }

    @Test
    fun `nom de fichier par defaut`() {
        assertEquals(
            "bibliotheque-20250830-1358.json",
            LibraryExporter.defaultFileName(1_756_562_291_000L, ZoneOffset.UTC),
        )
    }
}
