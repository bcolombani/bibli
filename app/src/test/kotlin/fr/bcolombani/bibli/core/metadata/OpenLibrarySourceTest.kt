package fr.bcolombani.bibli.core.metadata

import fr.bcolombani.bibli.core.http.HttpFetcher
import fr.bcolombani.bibli.support.Fixtures
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpenLibrarySourceTest {

    private val source = OpenLibrarySource(HttpFetcher(OkHttpClient()))

    @Test
    fun `reponse trouvee`() {
        val result = source.parse(Fixtures.read("open_library_found.json"))
        assertEquals("L'Étranger", result?.title)
        assertEquals("Albert Camus, Jean Grenier", result?.authors)
        assertEquals(BookSource.OPEN_LIBRARY, result?.source)
    }

    @Test
    fun `reponse vide`() {
        assertNull(source.parse(Fixtures.read("open_library_empty.json")))
    }

    @Test
    fun `reponse malformee`() {
        assertNull(source.parse(Fixtures.read("open_library_malformed.json")))
    }
}
