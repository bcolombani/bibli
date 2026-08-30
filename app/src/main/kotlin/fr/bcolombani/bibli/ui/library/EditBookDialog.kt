package fr.bcolombani.bibli.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.bcolombani.bibli.core.metadata.BookSource
import fr.bcolombani.bibli.core.model.Book
import fr.bcolombani.bibli.ui.common.label

/**
 * Édition d'une fiche. Toute modification bascule la source sur
 * [BookSource.MANUAL] : la provenance affichée doit rester honnête.
 */
@Composable
fun EditBookDialog(
    book: Book,
    onDismiss: () -> Unit,
    onConfirm: (title: String, authors: String) -> Unit,
) {
    var title by rememberSaveable(book.id) { mutableStateOf(book.title) }
    var authors by rememberSaveable(book.id) { mutableStateOf(book.authors) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Modifier la fiche") },
        text = {
            Column {
                Text(
                    text = "ISBN ${book.isbn13}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Titre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = authors,
                    onValueChange = { authors = it },
                    label = { Text("Auteur(s)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "La fiche sera marquée « ${BookSource.MANUAL.label} ».",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(title, authors) },
                enabled = title.isNotBlank(),
            ) {
                Text("Enregistrer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        },
    )
}
