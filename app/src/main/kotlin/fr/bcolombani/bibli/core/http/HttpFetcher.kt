package fr.bcolombani.bibli.core.http

import java.io.IOException
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Petit wrapper suspendu au-dessus d'OkHttp.
 *
 * - `enqueue` + [suspendCancellableCoroutine] : l'annulation de la coroutine
 *   (donc un `withTimeout` amont) annule réellement l'appel réseau ;
 * - toute erreur (IO, HTTP non-2xx, corps illisible) devient `null` : les appelants
 *   traitent « erreur » et « non trouvé » de la même façon.
 */
class HttpFetcher(private val client: OkHttpClient) {

    suspend fun getString(url: String, headers: Map<String, String> = emptyMap()): String? {
        val request = Request.Builder().url(url).apply {
            headers.forEach { (k, v) -> header(k, v) }
        }.build()

        val call = client.newCall(request)
        return suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { runCatching { call.cancel() } }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resume(null)
                }

                override fun onResponse(call: Call, response: Response) {
                    val body = response.use {
                        if (!it.isSuccessful) null else runCatching { it.body.string() }.getOrNull()
                    }
                    if (cont.isActive) cont.resume(body)
                }
            })
        }
    }

    companion object {
        /** En-tête demandé explicitement par Open Library pour identifier l'application. */
        const val USER_AGENT =
            "Bibli/1.0 (application Android personnelle d'inventaire de bibliotheque; " +
                "https://github.com/bcolombani/bibli)"
    }
}
