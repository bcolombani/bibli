package fr.bcolombani.bibli.core.scan

import fr.bcolombani.bibli.core.isbn.IsbnCheck
import fr.bcolombani.bibli.core.isbn.IsbnValidator
import fr.bcolombani.bibli.core.metadata.BookSource
import fr.bcolombani.bibli.core.metadata.MetadataLookupChain
import fr.bcolombani.bibli.core.model.Book

/** Ce dont le traitement d'un scan a besoin côté persistance. */
interface BookStore {
    suspend fun findByIsbn(isbn13: String): Book?

    /** Insère et renvoie le livre créé, ou `null` si l'ISBN existait déjà (index unique). */
    suspend fun insert(book: Book): Book?
}

/**
 * Les quatre issues possibles d'un scan, telles qu'affichées par l'écran de scan.
 *
 * | Issue | Icône | Suite |
 * |---|---|---|
 * | [Added] | coche verte | retour immédiat au scan |
 * | [NeedsManualEntry] | warning orange | feuille de saisie manuelle |
 * | [NotAnIsbn] | croix rouge | retour immédiat au scan, rien d'enregistré |
 * | [AlreadyPresent] | coche bleue | retour immédiat au scan, pas de doublon |
 */
sealed interface ScanOutcome {
    data class Added(val book: Book) : ScanOutcome

    data class NeedsManualEntry(val isbn13: String, val rawScan: String) : ScanOutcome

    data class NotAnIsbn(val rawScan: String, val reason: IsbnCheck.Reason) : ScanOutcome

    data class AlreadyPresent(val book: Book) : ScanOutcome
}

/**
 * Enchaînement complet d'un scan : validation ISBN → détection de doublon →
 * chaîne de recherche de métadonnées → enregistrement.
 *
 * Kotlin pur (la persistance passe par [BookStore]) : entièrement testable en JVM.
 */
class ScanProcessor(
    private val store: BookStore,
    private val metadata: MetadataLookupChain,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    suspend fun process(rawScan: String): ScanOutcome {
        val isbn13 = when (val check = IsbnValidator.check(rawScan)) {
            is IsbnCheck.Valid -> check.isbn13
            is IsbnCheck.Invalid -> return ScanOutcome.NotAnIsbn(rawScan, check.reason)
        }

        store.findByIsbn(isbn13)?.let { return ScanOutcome.AlreadyPresent(it) }

        val found = metadata.lookup(isbn13)
            ?: return ScanOutcome.NeedsManualEntry(isbn13, rawScan)

        val inserted = store.insert(
            Book(
                id = 0,
                isbn13 = isbn13,
                rawScan = rawScan,
                title = found.title,
                authors = found.authors,
                source = found.source,
                addedAt = clock(),
            ),
        )
        // `null` : un scan concurrent a inséré le même ISBN entre-temps.
        return inserted?.let { ScanOutcome.Added(it) }
            ?: ScanOutcome.AlreadyPresent(store.findByIsbn(isbn13)!!)
    }

    /** Enregistrement d'une fiche saisie à la main après un scan « orange ». */
    suspend fun saveManual(isbn13: String, rawScan: String, title: String, authors: String): Book? =
        store.insert(
            Book(
                id = 0,
                isbn13 = isbn13,
                rawScan = rawScan,
                title = title.trim(),
                authors = authors.trim(),
                source = BookSource.MANUAL,
                addedAt = clock(),
            ),
        )
}
