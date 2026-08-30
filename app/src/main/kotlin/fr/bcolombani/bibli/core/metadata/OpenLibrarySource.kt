package fr.bcolombani.bibli.core.metadata

import fr.bcolombani.bibli.core.http.HttpFetcher
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Open Library :
 * `https://openlibrary.org/api/books?bibkeys=ISBN:{isbn13}&format=json&jscmd=data`
 *
 * La réponse est un objet dont la clé est le bibkey (`ISBN:978…`) ; un livre inconnu
 * renvoie simplement `{}`. Open Library demande explicitement un `User-Agent` identifiant
 * l'application appelante.
 */
class OpenLibrarySource(
    private val http: HttpFetcher,
    private val baseUrl: String = DEFAULT_BASE_URL,
) : BookMetadataSource {

    override val source: BookSource = BookSource.OPEN_LIBRARY

    override suspend fun lookup(isbn13: String): BookMetadata? {
        val body = http.getString(
            "$baseUrl?bibkeys=ISBN:$isbn13&format=json&jscmd=data",
            mapOf("User-Agent" to HttpFetcher.USER_AGENT),
        ) ?: return null
        return parse(body)
    }

    internal fun parse(body: String): BookMetadata? {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        // On prend la première (et normalement unique) entrée, quel que soit le bibkey renvoyé.
        val entry = root.values.firstOrNull() as? JsonObject ?: return null
        val title = buildTitle(
            entry["title"]?.jsonPrimitive?.contentOrNull,
            entry["subtitle"]?.jsonPrimitive?.contentOrNull,
        ) ?: return null
        val authors = (entry["authors"] as? JsonArray).orEmptyNames()
        return BookMetadata(title = title, authors = authors.joinAuthors(), source = source)
    }

    private fun JsonArray?.orEmptyNames(): List<String> =
        this?.mapNotNull { (it as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull } ?: emptyList()

    companion object {
        const val DEFAULT_BASE_URL = "https://openlibrary.org/api/books"
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    }
}
