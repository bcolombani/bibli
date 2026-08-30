package fr.bcolombani.bibli.core.metadata

/** Provenance de la fiche d'un livre. Persisté tel quel (nom de l'enum) en base et à l'export. */
enum class BookSource {
    GOOGLE_BOOKS,
    OPEN_LIBRARY,
    BNF,
    MANUAL,
    ;

    companion object {
        /** Tolérant : une valeur inconnue en base retombe sur [MANUAL] plutôt que de planter. */
        fun fromName(name: String?): BookSource =
            entries.firstOrNull { it.name == name } ?: MANUAL
    }
}

/** Métadonnées minimales d'un livre en v1 : titre + auteurs aplatis. */
data class BookMetadata(
    val title: String,
    val authors: String,
    val source: BookSource,
)

/**
 * Une source de métadonnées interrogeable par ISBN-13.
 *
 * Contrat : ne jamais lever d'exception pour un échec « métier » (réseau, 4xx, 5xx,
 * JSON/XML malformé, livre absent) — retourner `null`. Seule l'annulation de la
 * coroutine doit remonter.
 */
interface BookMetadataSource {
    val source: BookSource

    suspend fun lookup(isbn13: String): BookMetadata?
}
