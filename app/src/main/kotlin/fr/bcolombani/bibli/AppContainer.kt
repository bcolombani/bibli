package fr.bcolombani.bibli

import android.content.Context
import fr.bcolombani.bibli.core.http.HttpFetcher
import fr.bcolombani.bibli.core.metadata.BnfSruSource
import fr.bcolombani.bibli.core.metadata.BookMetadataSource
import fr.bcolombani.bibli.core.metadata.GoogleBooksSource
import fr.bcolombani.bibli.core.metadata.MetadataLookupChain
import fr.bcolombani.bibli.core.metadata.OpenLibrarySource
import fr.bcolombani.bibli.core.scan.ScanProcessor
import fr.bcolombani.bibli.data.db.BibliDatabase
import fr.bcolombani.bibli.data.repo.BookRepository
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

/**
 * Injection de dépendances « à la main » : un conteneur unique construit dans
 * [BibliApplication]. Pas de Hilt, donc pas de génération de code supplémentaire
 * ni de plugin à faire vivre dans la CI.
 */
class AppContainer(context: Context) {

    private val okHttp: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private val http: HttpFetcher by lazy { HttpFetcher(okHttp) }

    private val database: BibliDatabase by lazy { BibliDatabase.build(context) }

    val repository: BookRepository by lazy { BookRepository(database.bookDao()) }

    /**
     * **L'ordre des sources est défini ici, et nulle part ailleurs.**
     * Réordonner la chaîne de repli tient donc à déplacer une ligne.
     */
    private val metadataSources: List<BookMetadataSource> by lazy {
        listOf(
            GoogleBooksSource(http),
            OpenLibrarySource(http),
            BnfSruSource(http),
        )
    }

    val metadataChain: MetadataLookupChain by lazy { MetadataLookupChain(metadataSources) }

    val scanProcessor: ScanProcessor by lazy { ScanProcessor(repository, metadataChain) }

    private companion object {
        /** Aligné sur le budget par source de [MetadataLookupChain]. */
        const val TIMEOUT_SECONDS = 5L
    }
}
