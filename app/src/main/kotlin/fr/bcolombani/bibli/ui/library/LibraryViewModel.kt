package fr.bcolombani.bibli.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fr.bcolombani.bibli.core.export.LibraryExporter
import fr.bcolombani.bibli.core.library.LibraryFilter
import fr.bcolombani.bibli.core.library.SearchScope
import fr.bcolombani.bibli.core.library.SortOrder
import fr.bcolombani.bibli.core.model.Book
import fr.bcolombani.bibli.data.repo.BookRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LibraryUiState(
    val query: String = "",
    val scope: SearchScope = SearchScope.ALL,
    val sort: SortOrder = SortOrder.ADDED_DESC,
)

/** Livre supprimé, conservé le temps de proposer « Annuler » dans une Snackbar. */
data class PendingDeletion(val book: Book)

class LibraryViewModel(private val repository: BookRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private val _pendingDeletion = MutableStateFlow<PendingDeletion?>(null)
    val pendingDeletion: StateFlow<PendingDeletion?> = _pendingDeletion.asStateFlow()

    /** Le `Flow` Room alimente directement la liste : elle se met à jour après chaque scan. */
    private val allBooks: StateFlow<List<Book>> = repository.observeBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val books: StateFlow<List<Book>> = combine(allBooks, _uiState) { books, ui ->
        LibraryFilter.apply(books, ui.query, ui.scope, ui.sort)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Vrai seulement si la bibliothèque est réellement vide (et non filtrée à vide). */
    val libraryEmpty: StateFlow<Boolean> = allBooks
        .map { it.isEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun onQueryChange(query: String) = _uiState.update { it.copy(query = query) }

    fun onScopeChange(scope: SearchScope) = _uiState.update { it.copy(scope = scope) }

    fun onSortChange(sort: SortOrder) = _uiState.update { it.copy(sort = sort) }

    fun delete(book: Book) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.delete(book) }
            _pendingDeletion.value = PendingDeletion(book)
        }
    }

    fun undoDeletion() {
        val pending = _pendingDeletion.value ?: return
        _pendingDeletion.value = null
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.restore(pending.book) }
        }
    }

    fun clearPendingDeletion() {
        _pendingDeletion.value = null
    }

    /** Une correction à la main bascule la fiche sur la source `MANUAL`. */
    fun edit(book: Book, title: String, authors: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.updateManually(book, title, authors) }
        }
    }

    /** Contenu du fichier d'export, calculé sur la bibliothèque entière (pas sur le filtre). */
    suspend fun buildExportJson(nowMillis: Long): String = withContext(Dispatchers.IO) {
        LibraryExporter.toJson(repository.snapshot(), nowMillis)
    }

    companion object {
        fun factory(repository: BookRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    LibraryViewModel(repository) as T
            }
    }
}
