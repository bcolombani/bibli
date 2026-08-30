// Module unique : la racine ne fait que déclarer les plugins pour les sous-projets.
// Depuis AGP 9, le support Kotlin est intégré : pas de plugin `org.jetbrains.kotlin.android`.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}
