package fr.bcolombani.bibli.ui.scan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.bcolombani.bibli.ui.common.findActivity

@Composable
fun ScanScreen(
    viewModel: ScanViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var permissionRequested by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        permissionRequested = true
    }

    // Permission demandée dès l'ouverture : l'application démarre en mode scan.
    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // Écran maintenu allumé pendant tout le scan en chaîne.
    val activity = context.findActivity()
    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (hasPermission) {
            CameraScanner(
                analysing = state.analysing,
                torchOn = state.torchOn,
                onBarcode = viewModel::onBarcode,
                onTorchAvailability = viewModel::onTorchAvailability,
                modifier = Modifier.fillMaxSize(),
            )
            ScanHud(
                state = state,
                onToggleTorch = viewModel::toggleTorch,
            )
            state.overlay?.let { ScanResultOverlay(it) }
        } else {
            CameraRationale(
                alreadyRefused = permissionRequested,
                onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                onOpenSettings = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
            )
        }
    }

    state.manualEntry?.let { request ->
        ManualEntrySheet(
            request = request,
            onSave = viewModel::saveManualEntry,
            onDismiss = viewModel::dismissManualEntry,
        )
    }
}

/** Compteur de session, indicateur de lookup et bouton torche. */
@Composable
private fun BoxScope.ScanHud(
    state: ScanUiState,
    onToggleTorch: () -> Unit,
) {
    Row(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Surface(
            color = Color.Black.copy(alpha = 0.55f),
            contentColor = Color.White,
            shape = RoundedCornerShape(50),
        ) {
            Text(
                text = "${state.sessionCount} livre${if (state.sessionCount > 1) "s" else ""} " +
                    "ajouté${if (state.sessionCount > 1) "s" else ""} · " +
                    "${state.libraryCount} en tout",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            // Indicateur discret de lookup réseau : jamais de dialogue modal.
            if (state.lookupInProgress) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = Color.White,
                )
                Spacer(Modifier.size(12.dp))
            }
            if (state.torchAvailable) {
                FilledIconToggleButton(checked = state.torchOn, onCheckedChange = { onToggleTorch() }) {
                    Icon(
                        imageVector = if (state.torchOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                        contentDescription = if (state.torchOn) "Éteindre la torche" else "Allumer la torche",
                    )
                }
            }
        }
    }
}

/**
 * Overlay central : ~140 dp, sur un fond semi-opaque pour rester lisible quel que soit
 * le flux caméra derrière. Non bloquant — le scan continue pendant son affichage.
 */
@Composable
private fun BoxScope.ScanResultOverlay(overlay: ScanOverlay) {
    val visual = overlay.kind.visual()
    Column(
        modifier = Modifier
            .align(Alignment.Center)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color.Black.copy(alpha = 0.62f),
        ) {
            Icon(
                imageVector = visual.icon,
                contentDescription = visual.contentDescription,
                tint = visual.color,
                modifier = Modifier.padding(16.dp).size(140.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.Black.copy(alpha = 0.62f),
            contentColor = Color.White,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = overlay.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                if (overlay.subtitle.isNotBlank()) {
                    Text(
                        text = overlay.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun CameraRationale(
    alreadyRefused: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.PhotoCamera,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = "L'appareil photo est nécessaire",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Bibli lit les codes-barres ISBN au dos des livres. " +
                "Aucune photo n'est enregistrée ni envoyée : l'image sert uniquement " +
                "à décoder le code, sur l'appareil.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = if (alreadyRefused) onOpenSettings else onRequest) {
            Text(if (alreadyRefused) "Ouvrir les réglages" else "Autoriser l'appareil photo")
        }
    }
}
