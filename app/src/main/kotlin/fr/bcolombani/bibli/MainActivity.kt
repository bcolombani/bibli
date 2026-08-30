package fr.bcolombani.bibli

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import fr.bcolombani.bibli.ui.BibliApp
import fr.bcolombani.bibli.ui.theme.BibliTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val container = (application as BibliApplication).container

        setContent {
            BibliTheme {
                BibliApp(container = container)
            }
        }
    }
}
