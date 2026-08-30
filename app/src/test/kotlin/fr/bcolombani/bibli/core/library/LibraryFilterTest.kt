package fr.bcolombani.bibli.core.library

import fr.bcolombani.bibli.core.metadata.BookSource
import fr.bcolombani.bibli.core.model.Book
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryFilterTest {

    private fun book(
        id: Long,
        title: String,
        authors: String,
        isbn: String = "978207036002$id",
        addedAt: Long = id,
    ) = Book(id, isbn, isbn, title, authors, BookSource.GOOGLE_BOOKS, addedAt)

    private val library = listOf(
        book(1, "L'Étranger", "Albert Camus", "9782070360024", addedAt = 300),
        book(2, "La Peste", "Albert Camus", "9782070360420", addedAt = 200),
        book(3, "Zazie dans le métro", "Raymond Queneau", "9782070361038", addedAt = 100),
    )

    private fun titles(query: String, scope: SearchScope, sort: SortOrder = SortOrder.ADDED_DESC) =
        LibraryFilter.apply(library, query, scope, sort).map { it.title }

    @Test
    fun `recherche insensible a la casse et aux accents`() {
        assertEquals(listOf("L'Étranger"), titles("etranger", SearchScope.ALL))
        assertEquals(listOf("L'Étranger"), titles("ÉTRANGER", SearchScope.TITLE))
        assertEquals(listOf("Zazie dans le métro"), titles("METRO", SearchScope.TITLE))
    }

    @Test
    fun `portee auteur`() {
        assertEquals(listOf("L'Étranger", "La Peste"), titles("camus", SearchScope.AUTHOR))
        assertEquals(emptyList<String>(), titles("camus", SearchScope.TITLE))
    }

    @Test
    fun `portee ISBN avec ou sans tirets`() {
        assertEquals(listOf("La Peste"), titles("9782070360420", SearchScope.ISBN))
        assertEquals(listOf("La Peste"), titles("978-2-07-036042-0", SearchScope.ISBN))
        assertEquals(listOf("La Peste"), titles("036042", SearchScope.ALL))
    }

    @Test
    fun `requete vide renvoie tout`() {
        assertEquals(3, titles("   ", SearchScope.ALL).size)
    }

    @Test
    fun `tris`() {
        assertEquals(
            listOf("L'Étranger", "La Peste", "Zazie dans le métro"),
            titles("", SearchScope.ALL, SortOrder.ADDED_DESC),
        )
        // Ordre alphabétique réel : la ponctuation ne doit pas remonter « L'Étranger ».
        assertEquals(
            listOf("La Peste", "L'Étranger", "Zazie dans le métro"),
            titles("", SearchScope.ALL, SortOrder.TITLE_ASC),
        )
        // Même auteur : départage par titre, toujours sans tenir compte de la ponctuation.
        assertEquals(
            listOf("La Peste", "L'Étranger", "Zazie dans le métro"),
            titles("", SearchScope.ALL, SortOrder.AUTHOR_ASC),
        )
    }
}
