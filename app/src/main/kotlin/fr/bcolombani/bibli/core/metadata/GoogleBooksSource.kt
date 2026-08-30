package fr.bcolombani.bibli.core.metadata

import fr.bcolombani.bibli.core.http.HttpFetcher
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Google Books, sans clé d'API :
 * `https://www.googleapis.com/books/v1/volumes?q=isbn:{isbn13}`
 */
class GoogleBooksSource(
    private val http: HttpFetcher,
    private val baseUrl: String = DEFAULT_BASE_URL,
) : BookMetadataSource {

    override val source: BookSource = BookSource.GOOGLE_BOOKS

    override suspend fun lookup(isbn13: String): BookMetadata? {
        val body = http.getString(
            "$baseUrl?q=isbn:$isbn13",
            mapOf("User-Agent" to HttpFetcher.USER_AGENT),
        ) ?: return null
        return parse(body)
    }

    internal fun parse(body: String): BookMetadata? {
        val payload = runCatching { json.decodeFromString<Payload>(body) }.getOrNull() ?: return null
        val info = payload.items.orEmpty().firstNotNullOfOrNull { it.volumeInfo } ?: return null
        val title = buildTitle(info.title, info.subtitle) ?: return null
        return BookMetadata(
            title = title,
            authors = info.authors.orEmpty().joinAuthors(),
            source = source,
        )
    }

    @Serializable
    private data class Payload(val items: List<Item>? = null)

    @Serializable
    private data class Item(@SerialName("volumeInfo") val volumeInfo: VolumeInfo? = null)

    @Serializable
    private data class VolumeInfo(
        val title: String? = null,
        val subtitle: String? = null,
        val authors: List<String>? = null,
    )

    companion object {
        const val DEFAULT_BASE_URL = "https://www.googleapis.com/books/v1/volumes"
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    }
}
