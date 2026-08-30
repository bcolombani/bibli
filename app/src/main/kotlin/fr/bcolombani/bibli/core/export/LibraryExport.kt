package fr.bcolombani.bibli.core.export

import fr.bcolombani.bibli.core.model.Book
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BookExportDto(
    val isbn13: String,
    val title: String,
    val authors: String,
    val source: String,
    val addedAt: String,
)

/**
 * Enveloppe du fichier d'export.
 *
 * [schemaVersion] est là pour un futur import : il permettra de reconnaître et de migrer
 * les fichiers produits par cette version. L'import n'est pas implémenté en v1.
 */
@Serializable
data class LibraryExportFile(
    val schemaVersion: Int = SCHEMA_VERSION,
    val exportedAt: String,
    val count: Int,
    val books: List<BookExportDto>,
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }
}

/** Sérialisation de la bibliothèque vers le JSON documenté dans le README. */
object LibraryExporter {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    private val ISO_SECONDS: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC)

    /** Instant epoch-millis → `2026-08-30T14:12:00Z` (UTC, à la seconde). */
    fun formatInstant(epochMillis: Long): String = ISO_SECONDS.format(Instant.ofEpochMilli(epochMillis))

    fun buildExport(books: List<Book>, exportedAtMillis: Long): LibraryExportFile = LibraryExportFile(
        exportedAt = formatInstant(exportedAtMillis),
        count = books.size,
        books = books.map {
            BookExportDto(
                isbn13 = it.isbn13,
                title = it.title,
                authors = it.authors,
                source = it.source.name,
                addedAt = formatInstant(it.addedAt),
            )
        },
    )

    fun toJson(books: List<Book>, exportedAtMillis: Long): String =
        json.encodeToString(buildExport(books, exportedAtMillis))

    /** Nom de fichier proposé au SAF : `bibliotheque-AAAAMMJJ-HHmm.json` (heure locale). */
    fun defaultFileName(epochMillis: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String {
        val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")
            .withZone(zone)
            .format(Instant.ofEpochMilli(epochMillis))
        return "bibliotheque-$stamp.json"
    }
}
