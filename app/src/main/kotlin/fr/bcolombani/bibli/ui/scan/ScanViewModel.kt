package fr.bcolombani.bibli.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fr.bcolombani.bibli.core.isbn.IsbnCheck
import fr.bcolombani.bibli.core.scan.ScanOutcome
import fr.bcolombani.bibli.core.scan.ScanProcessor
import fr.bcolombani.bibli.data.repo.BookRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Demande de saisie manuelle ouverte après un scan « orange ». */
data class ManualEntryRequest(
    val isbn13: String,
    val rawScan: String,
)

/** Overlay affiché au centre de l'écran après un scan. */
data class ScanOverlay(
    val kind: ScanFeedbackKind,
    val title: String,
    val subtitle: String,
)

data class ScanUiState(
    val sessionCount: Int = 0,
    val libraryCount: Int = 0,
    val lookupInProgress: Boolean = false,
    val overlay: ScanOverlay? = null,
    val manualEntry: ManualEntryRequest? = null,
    val torchOn: Boolean = false,
    val torchAvailable: Boolean = false,
) {
    /** La caméra n'analyse plus tant que la feuille de saisie manuelle est ouverte. */
    val analysing: Boolean get() = manualEntry == null
}

/**
 * Pilotage de l'écran de scan.
 *
 * Règles d'enchaînement :
 *  - un même code relu dans les [DEBOUNCE_MS] millisecondes est ignoré ;
 *  - aucun nouveau code n'est traité tant qu'un lookup est en cours ;
 *  - aucun code n'est traité tant que la feuille de saisie manuelle est ouverte ;
 *  - l'overlay vert / bleu / rouge s'efface seul après [OVERLAY_MS], sans rien bloquer.
 */
class ScanViewModel(
    private val processor: ScanProcessor,
    private val repository: BookRepository,
    private val feedback: ScanFeedback,
) : ViewModel() {

    private val _state = MutableStateFlow(ScanUiState())
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    private var lastCode: String? = null
    private var lastCodeAt: Long = 0L
    private var overlayJob: Job? = null
    private var processing = false

    init {
        repository.observeCount()
            .onEach { count -> _state.update { it.copy(libraryCount = count) } }
            .launchIn(viewModelScope)
    }

    fun onBarcode(rawValue: String, now: Long = System.currentTimeMillis()) {
        val current = _state.value
        if (processing || current.manualEntry != null) return
        if (rawValue == lastCode && now - lastCodeAt < DEBOUNCE_MS) return

        lastCode = rawValue
        lastCodeAt = now
        processing = true

        viewModelScope.launch {
            _state.update { it.copy(lookupInProgress = true) }
            // Le réseau et la base ne touchent jamais au thread du flux caméra.
            val outcome = withContext(Dispatchers.IO) { processor.process(rawValue) }
            _state.update { it.copy(lookupInProgress = false) }
            handle(outcome)
            processing = false
        }
    }

    private fun handle(outcome: ScanOutcome) {
        when (outcome) {
            is ScanOutcome.Added -> {
                feedback.play(ScanFeedbackKind.ADDED)
                _state.update { it.copy(sessionCount = it.sessionCount + 1) }
                showOverlay(
                    ScanOverlay(ScanFeedbackKind.ADDED, outcome.book.title, outcome.book.authors),
                )
            }

            is ScanOutcome.AlreadyPresent -> {
                feedback.play(ScanFeedbackKind.DUPLICATE)
                showOverlay(
                    ScanOverlay(
                        ScanFeedbackKind.DUPLICATE,
                        "Déjà dans la bibliothèque",
                        outcome.book.title,
                    ),
                )
            }

            is ScanOutcome.NotAnIsbn -> {
                feedback.play(ScanFeedbackKind.REJECTED)
                showOverlay(
                    ScanOverlay(
                        ScanFeedbackKind.REJECTED,
                        "Pas un ISBN",
                        outcome.reason.label(),
                    ),
                )
            }

            is ScanOutcome.NeedsManualEntry -> {
                feedback.play(ScanFeedbackKind.NOT_FOUND)
                overlayJob?.cancel()
                _state.update {
                    it.copy(
                        overlay = ScanOverlay(
                            ScanFeedbackKind.NOT_FOUND,
                            "Livre introuvable",
                            outcome.isbn13,
                        ),
                        manualEntry = ManualEntryRequest(outcome.isbn13, outcome.rawScan),
                    )
                }
            }
        }
    }

    private fun showOverlay(overlay: ScanOverlay) {
        overlayJob?.cancel()
        _state.update { it.copy(overlay = overlay) }
        overlayJob = viewModelScope.launch {
            delay(OVERLAY_MS)
            _state.update { it.copy(overlay = null) }
        }
    }

    fun saveManualEntry(title: String, authors: String) {
        val request = _state.value.manualEntry ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                processor.saveManual(request.isbn13, request.rawScan, title, authors)
            }
            _state.update { it.copy(sessionCount = it.sessionCount + 1) }
            dismissManualEntry()
        }
    }

    fun dismissManualEntry() {
        // Le code vient d'être traité : on le laisse dans l'anti-rebond pour ne pas
        // rouvrir la feuille immédiatement en repassant devant l'objectif.
        lastCodeAt = System.currentTimeMillis()
        _state.update { it.copy(manualEntry = null, overlay = null) }
    }

    fun toggleTorch() {
        _state.update { it.copy(torchOn = !it.torchOn) }
    }

    fun onTorchAvailability(available: Boolean) {
        _state.update { it.copy(torchAvailable = available) }
    }

    companion object {
        const val DEBOUNCE_MS = 3_000L
        const val OVERLAY_MS = 800L

        fun factory(
            processor: ScanProcessor,
            repository: BookRepository,
            feedback: ScanFeedback,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ScanViewModel(processor, repository, feedback) as T
        }
    }
}

private fun IsbnCheck.Reason.label(): String = when (this) {
    IsbnCheck.Reason.BAD_LENGTH -> "Code-barres non ISBN"
    IsbnCheck.Reason.BAD_CHARACTER -> "Code-barres non ISBN"
    IsbnCheck.Reason.NOT_BOOKLAND -> "Code-barres produit, pas un livre"
    IsbnCheck.Reason.ISMN -> "Partition (ISMN), pas un livre"
    IsbnCheck.Reason.BAD_CHECKSUM -> "Clé de contrôle invalide"
}
