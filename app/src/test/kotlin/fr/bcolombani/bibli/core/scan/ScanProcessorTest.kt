package fr.bcolombani.bibli.core.scan

import fr.bcolombani.bibli.core.isbn.IsbnCheck
import fr.bcolombani.bibli.core.metadata.BookMetadata
import fr.bcolombani.bibli.core.metadata.BookMetadataSource
import fr.bcolombani.bibli.core.metadata.BookSource
import fr.bcolombani.bibli.core.metadata.MetadataLookupChain
import fr.bcolombani.bibli.core.model.Book
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanProcessorTest {

    /** Store en mémoire respectant la contrainte d'unicité sur l'ISBN. */
    private class FakeStore : BookStore {
        val books = mutableListOf<Book>()
        private var nextId = 1L

        override suspend fun findByIsbn(isbn13: String): Book? = books.firstOrNull { it.isbn13 == isbn13 }

        override suspend fun insert(book: Book): Book? {
            if (findByIsbn(book.isbn13) != null) return null
            val stored = book.copy(id = nextId++)
            books += stored
            return stored
        }
    }

    private class FixedSource(
        override val source: BookSource,
        private val answer: BookMetadata?,
    ) : BookMetadataSource {
        override suspend fun lookup(isbn13: String): BookMetadata? = answer
    }

    private fun processor(answer: BookMetadata?, store: BookStore = FakeStore()) = ScanProcessor(
        store = store,
        metadata = MetadataLookupChain(listOf(FixedSource(BookSource.GOOGLE_BOOKS, answer))),
        clock = { 1_000L },
    )

    @Test
    fun `ISBN valide et trouve est enregistre`() = runBlocking {
        val store = FakeStore()
        val outcome = processor(
            BookMetadata("L'Étranger", "Albert Camus", BookSource.GOOGLE_BOOKS),
            store,
        ).process("978-2-07-036002-4")

        val added = outcome as ScanOutcome.Added
        assertEquals("9782070360024", added.book.isbn13)
        assertEquals("978-2-07-036002-4", added.book.rawScan)
        assertEquals(BookSource.GOOGLE_BOOKS, added.book.source)
        assertEquals(1_000L, added.book.addedAt)
        assertEquals(1, store.books.size)
    }

    @Test
    fun `ISBN valide mais introuvable demande une saisie manuelle`() = runBlocking {
        val store = FakeStore()
        val outcome = processor(null, store).process("9782070360024")

        assertEquals(ScanOutcome.NeedsManualEntry("9782070360024", "9782070360024"), outcome)
        assertTrue("rien ne doit être enregistré", store.books.isEmpty())
    }

    @Test
    fun `code non ISBN est rejete sans enregistrement`() = runBlocking {
        val store = FakeStore()
        val outcome = processor(
            BookMetadata("Ne devrait pas servir", "", BookSource.GOOGLE_BOOKS),
            store,
        ).process("3017620422003")

        assertEquals(
            ScanOutcome.NotAnIsbn("3017620422003", IsbnCheck.Reason.NOT_BOOKLAND),
            outcome,
        )
        assertTrue(store.books.isEmpty())
    }

    @Test
    fun `ISMN est rejete`() = runBlocking {
        val outcome = processor(null).process("9790006134540")
        assertEquals(IsbnCheck.Reason.ISMN, (outcome as ScanOutcome.NotAnIsbn).reason)
    }

    @Test
    fun `ISBN deja present ne cree pas de doublon`() = runBlocking {
        val store = FakeStore()
        val meta = BookMetadata("L'Étranger", "Albert Camus", BookSource.GOOGLE_BOOKS)
        val first = processor(meta, store).process("9782070360024")
        val second = processor(meta, store).process("978-2-07-036002-4")

        assertTrue(first is ScanOutcome.Added)
        val already = second as ScanOutcome.AlreadyPresent
        assertEquals("9782070360024", already.book.isbn13)
        assertEquals(1, store.books.size)
    }

    @Test
    fun `un ISBN-10 rescane sous sa forme ISBN-13 est vu comme deja present`() = runBlocking {
        val store = FakeStore()
        val meta = BookMetadata("L'Étranger", "Albert Camus", BookSource.GOOGLE_BOOKS)
        processor(meta, store).process("2070360024")
        val second = processor(meta, store).process("9782070360024")

        assertTrue(second is ScanOutcome.AlreadyPresent)
        assertEquals(1, store.books.size)
    }

    @Test
    fun `saisie manuelle enregistre avec la source MANUAL`() = runBlocking {
        val store = FakeStore()
        val saved = processor(null, store)
            .saveManual("9782070360024", "9782070360024", "  Titre saisi  ", " Auteur saisi ")

        assertEquals("Titre saisi", saved?.title)
        assertEquals("Auteur saisi", saved?.authors)
        assertEquals(BookSource.MANUAL, saved?.source)
        assertEquals(1, store.books.size)
    }
}
