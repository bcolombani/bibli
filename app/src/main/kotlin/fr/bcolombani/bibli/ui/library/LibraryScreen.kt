package fr.bcolombani.bibli.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.bcolombani.bibli.core.export.LibraryExporter
import fr.bcolombani.bibli.core.library.SearchScope
import fr.bcolombani.bibli.core.library.SortOrder
import fr.bcolombani.bibli.core.model.Book
import fr.bcolombani.bibli.ui.common.SourceBadge
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    modifier: Modifier = Modifier,
) {
    val books by viewModel.books.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val libraryEmpty by viewModel.libraryEmpty.collectAsStateWithLifecycle()
    val pendingDeletion by viewModel.pendingDeletion.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<Book?>(null) }
    var exporting by remember { mutableStateOf(false) }

    // SAF : aucune permission de stockage, l'utilisateur choisit lui-même l'emplacement.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(EXPORT_MIME),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        exporting = true
        scope.launch {
            val json = viewModel.buildExportJson(System.currentTimeMillis())
            val written = runCatching {
                val stream = context.contentResolver.openOutputStream(uri)
                    ?: error("flux d'écriture indisponible pour \$uri")
                stream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            }
            exporting = false
            snackbarHostState.showSnackbar(
                if (written.isSuccess) "Bibliothèque exportée" else "Échec de l'export",
            )
        }
    }

    LaunchedEffect(pendingDeletion) {
        val pending = pendingDeletion ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = "« ${pending.book.title} » supprimé",
            actionLabel = "Annuler",
            withDismissAction = true,
        )
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.undoDeletion()
        } else {
            viewModel.clearPendingDeletion()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Bibliothèque") },
                actions = {
                    if (exporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp).padding(end = 4.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                    IconButton(
                        onClick = {
                            exportLauncher.launch(
                                LibraryExporter.defaultFileName(System.currentTimeMillis()),
                            )
                        },
                        enabled = !libraryEmpty && !exporting,
                    ) {
                        Icon(Icons.Filled.FileUpload, contentDescription = "Exporter en JSON")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            SearchAndSortBar(
                query = uiState.query,
                scope = uiState.scope,
                sort = uiState.sort,
                onQueryChange = viewModel::onQueryChange,
                onScopeChange = viewModel::onScopeChange,
                onSortChange = viewModel::onSortChange,
            )
            HorizontalDivider()

            when {
                libraryEmpty -> EmptyLibrary()
                books.isEmpty() -> NoResults(uiState.query)
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(books, key = { it.id }) { book ->
                        BookRow(
                            book = book,
                            onClick = { editing = book },
                            onLongClick = { viewModel.delete(book) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    editing?.let { book ->
        EditBookDialog(
            book = book,
            onDismiss = { editing = null },
            onConfirm = { title, authors ->
                viewModel.edit(book, title, authors)
                editing = null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchAndSortBar(
    query: String,
    scope: SearchScope,
    sort: SortOrder,
    onQueryChange: (String) -> Unit,
    onScopeChange: (SearchScope) -> Unit,
    onSortChange: (SortOrder) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Rechercher") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Effacer la recherche")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchScope.entries.forEach { candidate ->
                FilterChip(
                    selected = scope == candidate,
                    onClick = { onScopeChange(candidate) },
                    label = { Text(candidate.label) },
                )
            }
            Spacer(Modifier.size(8.dp))
            Text("Tri", style = MaterialTheme.typography.labelMedium)
            SortOrder.entries.forEach { candidate ->
                FilterChip(
                    selected = sort == candidate,
                    onClick = { onSortChange(candidate) },
                    label = { Text(candidate.label) },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookRow(
    book: Book,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = book.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (book.authors.isNotBlank()) {
            Text(
                text = book.authors,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SourceBadge(book.source)
            Spacer(Modifier.size(8.dp))
            Text(
                text = "${book.isbn13} · ${book.addedAt.formatAddedAt()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyLibrary() {
    CenteredMessage(
        title = "Bibliothèque vide",
        body = "Passe à l'onglet Scan et fais défiler les livres devant l'objectif : " +
            "ils apparaîtront ici au fur et à mesure.",
    )
}

@Composable
private fun NoResults(query: String) {
    CenteredMessage(
        title = "Aucun résultat",
        body = "Rien ne correspond à « $query » dans cette portée de recherche.",
    )
}

@Composable
private fun CenteredMessage(title: String, body: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.MenuBook,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private val SearchScope.label: String
    get() = when (this) {
        SearchScope.ALL -> "Tout"
        SearchScope.TITLE -> "Titre"
        SearchScope.AUTHOR -> "Auteur"
        SearchScope.ISBN -> "ISBN"
    }

private val SortOrder.label: String
    get() = when (this) {
        SortOrder.ADDED_DESC -> "Récents"
        SortOrder.TITLE_ASC -> "Titre"
        SortOrder.AUTHOR_ASC -> "Auteur"
    }

private val ADDED_AT_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.FRANCE)

private fun Long.formatAddedAt(): String =
    ADDED_AT_FORMAT.withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(this))

private const val EXPORT_MIME = "application/json"
