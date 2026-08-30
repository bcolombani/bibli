package fr.bcolombani.bibli.data.repo

import fr.bcolombani.bibli.core.metadata.BookSource
import fr.bcolombani.bibli.core.model.Book
import fr.bcolombani.bibli.core.scan.BookStore
import fr.bcolombani.bibli.data.db.BookDao
import fr.bcolombani.bibli.data.db.toBook
import fr.bcolombani.bibli.data.db.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Accès à la bibliothèque : implémentation Room de [BookStore] + flux pour l'UI. */
class BookRepository(private val dao: BookDao) : BookStore {

    fun observeBooks(): Flow<List<Book>> = dao.observeAll().map { list -> list.map { it.toBook() } }

    fun observeCount(): Flow<Int> = dao.observeCount()

    override suspend fun findByIsbn(isbn13: String): Book? = dao.findByIsbn(isbn13)?.toBook()

    override suspend fun insert(book: Book): Book? {
        val id = dao.insert(book.toEntity())
        // -1 : conflit sur l'index unique isbn13, le livre était déjà là.
        return if (id == -1L) null else book.copy(id = id)
    }

    /** Édition manuelle : bascule systématiquement la source sur [BookSource.MANUAL]. */
    suspend fun updateManually(book: Book, title: String, authors: String) {
        dao.update(
            book.copy(
                title = title.trim(),
                authors = authors.trim(),
                source = BookSource.MANUAL,
            ).toEntity(),
        )
    }

    suspend fun delete(book: Book) = dao.deleteById(book.id)

    /** Réinsertion à l'identique pour l'action « Annuler » d'une suppression. */
    suspend fun restore(book: Book) {
        dao.insert(book.toEntity())
    }

    suspend fun snapshot(): List<Book> = dao.snapshot().map { it.toBook() }
}
