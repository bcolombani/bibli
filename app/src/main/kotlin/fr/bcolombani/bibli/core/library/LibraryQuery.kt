package fr.bcolombani.bibli.core.library

import fr.bcolombani.bibli.core.model.Book
import java.text.Normalizer
import java.util.Locale

/** Portée de la recherche dans l'écran Bibliothèque. */
enum class SearchScope { ALL, TITLE, AUTHOR, ISBN }

/** Critère de tri de la liste. */
enum class SortOrder { ADDED_DESC, TITLE_ASC, AUTHOR_ASC }

/**
 * Filtrage et tri de la bibliothèque, en Kotlin pur.
 *
 * La recherche est faite en mémoire plutôt qu'en SQL : SQLite ne sait pas comparer
 * « Etranger » et « Étranger » sans extension, et l'insensibilité aux accents est
 * exactement ce qu'on veut quand on tape un titre au clavier.
 */
object LibraryFilter {

    private val COMBINING_MARKS = Regex("\\p{Mn}+")

    /** Minuscules + suppression des diacritiques : `L'Étranger` → `l'etranger`. */
    fun normalize(text: String): String =
        COMBINING_MARKS.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "")
            .lowercase(Locale.ROOT)

    /**
     * Clé de tri : [normalize] puis suppression de la ponctuation.
     *
     * Sans cela l'apostrophe, qui précède les lettres dans l'ordre des points de code,
     * classerait `L'Étranger` avant `La Peste` — ce n'est pas l'ordre alphabétique
     * attendu par un lecteur.
     */
    fun sortKey(text: String): String =
        normalize(text).filter { it.isLetterOrDigit() || it == ' ' }.trim()

    fun apply(
        books: List<Book>,
        query: String,
        scope: SearchScope,
        sort: SortOrder,
    ): List<Book> {
        val needle = normalize(query.trim())
        val filtered = if (needle.isEmpty()) books else books.filter { it.matches(needle, scope) }
        return filtered.sortedWith(comparatorFor(sort))
    }

    private fun Book.matches(needle: String, scope: SearchScope): Boolean = when (scope) {
        SearchScope.TITLE -> normalize(title).contains(needle)
        SearchScope.AUTHOR -> normalize(authors).contains(needle)
        // L'ISBN se cherche indifféremment avec ou sans tirets.
        SearchScope.ISBN -> isbn13.contains(needle.filter { it.isLetterOrDigit() })
        SearchScope.ALL ->
            normalize(title).contains(needle) ||
                normalize(authors).contains(needle) ||
                isbn13.contains(needle.filter { it.isLetterOrDigit() })
    }

    private fun comparatorFor(sort: SortOrder): Comparator<Book> = when (sort) {
        SortOrder.ADDED_DESC -> compareByDescending<Book> { it.addedAt }.thenBy { sortKey(it.title) }
        SortOrder.TITLE_ASC -> compareBy<Book> { sortKey(it.title) }.thenByDescending { it.addedAt }
        SortOrder.AUTHOR_ASC -> compareBy<Book> { sortKey(it.authors) }.thenBy { sortKey(it.title) }
    }
}
