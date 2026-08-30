package fr.bcolombani.bibli.ui.common

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.bcolombani.bibli.core.metadata.BookSource

/** Libellé court affiché sur la ligne d'un livre. */
val BookSource.label: String
    get() = when (this) {
        BookSource.GOOGLE_BOOKS -> "Google Books"
        BookSource.OPEN_LIBRARY -> "Open Library"
        BookSource.BNF -> "BnF"
        BookSource.MANUAL -> "Saisie manuelle"
    }

/**
 * Badge de provenance.
 *
 * `MANUAL` est volontairement le seul badge plein et contrasté : une fiche saisie
 * ou corrigée à la main n'a pas la même valeur qu'une fiche issue d'un catalogue,
 * et doit se repérer d'un coup d'œil dans la liste.
 */
@Composable
fun SourceBadge(source: BookSource, modifier: Modifier = Modifier) {
    val isManual = source == BookSource.MANUAL
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = if (isManual) {
            MaterialTheme.colorScheme.tertiary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = if (isManual) {
            MaterialTheme.colorScheme.onTertiary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    ) {
        Text(
            text = source.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isManual) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}
