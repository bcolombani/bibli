package fr.bcolombani.bibli.core.metadata

import fr.bcolombani.bibli.core.http.HttpFetcher
import java.io.StringReader
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/**
 * Catalogue général de la BnF, API SRU, notices Dublin Core.
 *
 * Cette source rattrape une grande partie des livres français absents de Google Books
 * et d'Open Library.
 *
 * Le parseur est injecté ([parserFactory]) : en production on utilise l'implémentation
 * de la plateforme Android, et les tests JVM fournissent une implémentation réelle
 * (le `android.jar` des tests unitaires ne contient que des stubs).
 */
class BnfSruSource(
    private val http: HttpFetcher,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val parserFactory: () -> XmlPullParser = ::platformParser,
) : BookMetadataSource {

    override val source: BookSource = BookSource.BNF

    override suspend fun lookup(isbn13: String): BookMetadata? {
        val query = "bib.isbn%20all%20%22$isbn13%22"
        val url = "$baseUrl?version=1.2&operation=searchRetrieve&query=$query" +
            "&recordSchema=dublincore&maximumRecords=1"
        val body = http.getString(url, mapOf("User-Agent" to HttpFetcher.USER_AGENT)) ?: return null
        return parse(body)
    }

    internal fun parse(body: String): BookMetadata? = runCatching {
        val parser = parserFactory()
        parser.setInput(StringReader(body))

        var title: String? = null
        val creators = mutableListOf<String>()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name.localPart()) {
                    "title" -> if (title == null) title = parser.nextTextOrNull()
                    "creator" -> parser.nextTextOrNull()?.let(creators::add)
                }
            }
            event = parser.next()
        }

        val cleanTitle = buildTitle(title, null) ?: return@runCatching null
        BookMetadata(
            title = cleanTitle,
            authors = creators.map(::cleanCreator).joinAuthors(),
            source = source,
        )
    }.getOrNull()

    /** `dc:title` → `title` : on travaille sans conscience des namespaces pour rester tolérant. */
    private fun String?.localPart(): String = this?.substringAfterLast(':').orEmpty()

    private fun XmlPullParser.nextTextOrNull(): String? =
        runCatching { nextText() }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * Les notices BnF suffixent souvent l'auteur de ses dates de vie :
     * `Camus, Albert (1913-1960)` → `Camus, Albert`.
     */
    private fun cleanCreator(raw: String): String =
        raw.replace(LIFE_DATES, "").trim().trimEnd(',', ';').trim()

    companion object {
        const val DEFAULT_BASE_URL = "https://catalogue.bnf.fr/api/SRU"

        private val LIFE_DATES = Regex("""\s*\((?=[^)]*\d{3})[^)]*\)\s*$""")

        private fun platformParser(): XmlPullParser =
            XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }.newPullParser()
    }
}
