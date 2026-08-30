package fr.bcolombani.bibli.core.metadata

import fr.bcolombani.bibli.core.http.HttpFetcher
import fr.bcolombani.bibli.support.Fixtures
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GoogleBooksSourceTest {

    private val source = GoogleBooksSource(HttpFetcher(OkHttpClient()))

    @Test
    fun `reponse trouvee`() {
        val result = source.parse(Fixtures.read("google_books_found.json"))
        assertEquals("L'Étranger : roman", result?.title)
        assertEquals("Albert Camus", result?.authors)
        assertEquals(BookSource.GOOGLE_BOOKS, result?.source)
    }

    @Test
    fun `reponse vide`() {
        assertNull(source.parse(Fixtures.read("google_books_empty.json")))
    }

    @Test
    fun `reponse malformee`() {
        assertNull(source.parse(Fixtures.read("google_books_malformed.json")))
    }
}
