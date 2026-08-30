package fr.bcolombani.bibli.core.model

import fr.bcolombani.bibli.core.metadata.BookSource

/**
 * Modèle métier d'un livre de la bibliothèque.
 *
 * Volontairement découplé de l'entité Room : c'est lui qui circule dans les ViewModels,
 * l'export et les tests JVM.
 */
data class Book(
    val id: Long,
    val isbn13: String,
    val rawScan: String,
    val title: String,
    val authors: String,
    val source: BookSource,
    val addedAt: Long,
)
