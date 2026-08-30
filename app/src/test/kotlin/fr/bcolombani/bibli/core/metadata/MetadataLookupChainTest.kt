package fr.bcolombani.bibli.core.metadata

import fr.bcolombani.bibli.core.http.HttpFetcher
import fr.bcolombani.bibli.support.Fixtures
import fr.bcolombani.bibli.support.testXmlPullParser
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Chaîne de repli de bout en bout : les trois sources réelles pointent sur un
 * [MockWebServer], chacune sur son propre chemin.
 */
class MetadataLookupChainTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient.Builder()
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun chain(
        perSourceTimeoutMs: Long = 1_000,
        totalBudgetMs: Long = 5_000,
    ): MetadataLookupChain {
        val http = HttpFetcher(client)
        return MetadataLookupChain(
            sources = listOf(
                GoogleBooksSource(http, server.url("/books").toString()),
                OpenLibrarySource(http, server.url("/openlibrary").toString()),
                BnfSruSource(http, server.url("/bnf").toString(), ::testXmlPullParser),
            ),
            perSourceTimeoutMs = perSourceTimeoutMs,
            totalBudgetMs = totalBudgetMs,
        )
    }

    private fun route(responses: Map<String, MockResponse>) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                val key = responses.keys.firstOrNull { path.startsWith(it) }
                return responses[key] ?: MockResponse().setResponseCode(404)
            }
        }
    }

    private fun body(fixture: String) =
        MockResponse().setResponseCode(200).setBody(Fixtures.read(fixture))

    @Test
    fun `premiere source vide, la deuxieme repond`() = runBlocking {
        route(
            mapOf(
                "/books" to body("google_books_empty.json"),
                "/openlibrary" to body("open_library_found.json"),
                "/bnf" to body("bnf_sru_found.xml"),
            ),
        )

        val result = chain().lookup("9782070360024")

        assertEquals(BookSource.OPEN_LIBRARY, result?.source)
        assertEquals("L'Étranger", result?.title)
    }

    @Test
    fun `les deux premieres sources vides, la BnF repond`() = runBlocking {
        route(
            mapOf(
                "/books" to body("google_books_empty.json"),
                "/openlibrary" to body("open_library_empty.json"),
                "/bnf" to body("bnf_sru_found.xml"),
            ),
        )

        val result = chain().lookup("9782070360024")

        assertEquals(BookSource.BNF, result?.source)
        assertEquals("Le Grand Meaulnes", result?.title)
    }

    @Test
    fun `les trois sources vides donnent aucun resultat`() = runBlocking {
        route(
            mapOf(
                "/books" to body("google_books_empty.json"),
                "/openlibrary" to body("open_library_empty.json"),
                "/bnf" to body("bnf_sru_empty.xml"),
            ),
        )

        assertNull(chain().lookup("9782070360024"))
    }

    @Test
    fun `une erreur serveur est traitee comme une absence de reponse`() = runBlocking {
        route(
            mapOf(
                "/books" to MockResponse().setResponseCode(500),
                "/openlibrary" to MockResponse().setResponseCode(429),
                "/bnf" to body("bnf_sru_found.xml"),
            ),
        )

        assertEquals(BookSource.BNF, chain().lookup("9782070360024")?.source)
    }

    @Test
    fun `une source en timeout ne bloque pas le repli`() = runBlocking {
        route(
            mapOf(
                // Google Books ne répondra jamais dans le budget de la source.
                "/books" to MockResponse().setBodyDelay(10, TimeUnit.SECONDS)
                    .setResponseCode(200).setBody("{}"),
                "/openlibrary" to body("open_library_found.json"),
                "/bnf" to body("bnf_sru_found.xml"),
            ),
        )

        val started = System.currentTimeMillis()
        val result = chain(perSourceTimeoutMs = 500, totalBudgetMs = 5_000).lookup("9782070360024")
        val elapsed = System.currentTimeMillis() - started

        assertEquals(BookSource.OPEN_LIBRARY, result?.source)
        // Le repli doit avoir lieu au timeout de la source, pas au bout des 10 s du serveur.
        org.junit.Assert.assertTrue("repli trop lent : $elapsed ms", elapsed < 5_000)
    }

    @Test
    fun `le budget global borne la chaine entiere`() = runBlocking {
        val slow = MockResponse().setBodyDelay(10, TimeUnit.SECONDS)
            .setResponseCode(200).setBody("{}")
        route(mapOf("/books" to slow, "/openlibrary" to slow, "/bnf" to slow))

        val started = System.currentTimeMillis()
        val result = chain(perSourceTimeoutMs = 5_000, totalBudgetMs = 900).lookup("9782070360024")
        val elapsed = System.currentTimeMillis() - started

        assertNull(result)
        org.junit.Assert.assertTrue("budget global non respecté : $elapsed ms", elapsed < 4_000)
    }
}
