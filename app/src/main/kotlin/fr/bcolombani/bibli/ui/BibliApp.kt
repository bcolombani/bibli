package fr.bcolombani.bibli.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import fr.bcolombani.bibli.AppContainer
import fr.bcolombani.bibli.ui.library.LibraryScreen
import fr.bcolombani.bibli.ui.library.LibraryViewModel
import fr.bcolombani.bibli.ui.scan.ScanFeedback
import fr.bcolombani.bibli.ui.scan.ScanScreen
import fr.bcolombani.bibli.ui.scan.ScanViewModel
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel

private enum class Destination(val route: String, val label: String, val icon: ImageVector) {
    SCAN("scan", "Scan", Icons.Filled.QrCodeScanner),
    LIBRARY("library", "Bibliothèque", Icons.AutoMirrored.Filled.MenuBook),
}

/**
 * Navigation à deux onglets. L'application **démarre sur l'écran de scan** :
 * ouvrir l'application, c'est déjà être en train de scanner.
 */
@Composable
fun BibliApp(container: AppContainer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val context = LocalContext.current
    val feedback = remember { ScanFeedback(context) }
    DisposableEffect(feedback) {
        onDispose { feedback.release() }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { destination ->
                    val selected = currentDestination?.hierarchy
                        ?.any { it.route == destination.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.SCAN.route,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            composable(Destination.SCAN.route) {
                val viewModel: ScanViewModel = viewModel(
                    factory = ScanViewModel.factory(
                        container.scanProcessor,
                        container.repository,
                        feedback,
                    ),
                )
                ScanScreen(viewModel = viewModel, modifier = Modifier.fillMaxSize())
            }
            composable(Destination.LIBRARY.route) {
                val viewModel: LibraryViewModel = viewModel(
                    factory = LibraryViewModel.factory(container.repository),
                )
                LibraryScreen(viewModel = viewModel, modifier = Modifier.fillMaxSize())
            }
        }
    }
}
