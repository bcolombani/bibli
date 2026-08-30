package fr.bcolombani.bibli.core.metadata

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Interroge les sources **séquentiellement** et s'arrête à la première réponse exploitable.
 *
 * L'ordre est celui de [sources] : c'est le seul endroit du code où il est défini
 * (voir `AppContainer`), donc réordonner les sources tient en une ligne.
 *
 * Budgets : [perSourceTimeoutMs] par source, [totalBudgetMs] pour la chaîne entière.
 * Une source lente ne peut donc pas bloquer le scan au-delà du budget global.
 */
class MetadataLookupChain(
    private val sources: List<BookMetadataSource>,
    private val perSourceTimeoutMs: Long = PER_SOURCE_TIMEOUT_MS,
    private val totalBudgetMs: Long = TOTAL_BUDGET_MS,
) {

    suspend fun lookup(isbn13: String): BookMetadata? = withTimeoutOrNull(totalBudgetMs) {
        for (source in sources) {
            val result = try {
                withTimeoutOrNull(perSourceTimeoutMs) { source.lookup(isbn13) }
            } catch (cancellation: CancellationException) {
                // Budget global épuisé (ou appelant annulé) : on laisse remonter.
                throw cancellation
            } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
                // Réseau, 4xx/5xx, parsing… : la source est simplement « sans réponse ».
                null
            }
            if (result != null && result.title.isNotBlank()) return@withTimeoutOrNull result
        }
        null
    }

    companion object {
        const val PER_SOURCE_TIMEOUT_MS = 5_000L
        const val TOTAL_BUDGET_MS = 12_000L
    }
}
