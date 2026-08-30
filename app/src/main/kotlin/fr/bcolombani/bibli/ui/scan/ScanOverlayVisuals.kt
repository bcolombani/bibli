package fr.bcolombani.bibli.ui.scan

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.LibraryAddCheck
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Couleur et icône de chaque issue.
 *
 * Les couleurs sont volontairement fixes (et non issues du thème dynamique) : le vert,
 * le bleu, l'orange et le rouge portent ici l'information, ils ne doivent pas changer
 * avec le fond d'écran de l'utilisateur.
 */
data class ScanVisual(
    val icon: ImageVector,
    val color: Color,
    val contentDescription: String,
)

fun ScanFeedbackKind.visual(): ScanVisual = when (this) {
    ScanFeedbackKind.ADDED -> ScanVisual(
        icon = Icons.Filled.CheckCircle,
        color = Color(0xFF2E7D32),
        contentDescription = "Livre ajouté",
    )

    ScanFeedbackKind.DUPLICATE -> ScanVisual(
        icon = Icons.Filled.LibraryAddCheck,
        color = Color(0xFF1565C0),
        contentDescription = "Déjà dans la bibliothèque",
    )

    ScanFeedbackKind.NOT_FOUND -> ScanVisual(
        icon = Icons.Filled.WarningAmber,
        color = Color(0xFFE65100),
        contentDescription = "Livre introuvable, saisie manuelle",
    )

    ScanFeedbackKind.REJECTED -> ScanVisual(
        icon = Icons.Filled.Cancel,
        color = Color(0xFFC62828),
        contentDescription = "Code refusé",
    )
}
