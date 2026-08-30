package fr.bcolombani.bibli.core.metadata

import fr.bcolombani.bibli.core.http.HttpFetcher
import fr.bcolombani.bibli.support.Fixtures
import fr.bcolombani.bibli.support.testXmlPullParser
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BnfSruSourceTest {

    private val source = BnfSruSource(
        http = HttpFetcher(OkHttpClient()),
        parserFactory = ::testXmlPullParser,
    )

    @Test
    fun `notice trouvee`() {
        val result = source.parse(Fixtures.read("bnf_sru_found.xml"))
        assertEquals("Le Grand Meaulnes", result?.title)
        // Les dates de vie accolées au nom sont retirées.
        assertEquals("Alain-Fournier, Daniel Leuwers", result?.authors)
        assertEquals(BookSource.BNF, result?.source)
    }

    @Test
    fun `aucune notice`() {
        assertNull(source.parse(Fixtures.read("bnf_sru_empty.xml")))
    }

    @Test
    fun `xml malforme`() {
        assertNull(source.parse(Fixtures.read("bnf_sru_malformed.xml")))
    }
}
