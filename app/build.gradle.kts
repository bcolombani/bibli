import java.util.Base64

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

/**
 * `versionCode` : numéro de run GitHub Actions, pour que deux APK successifs soient
 * toujours ordonnés. En local, 1.
 */
val buildVersionCode: Int =
    providers.environmentVariable("GITHUB_RUN_NUMBER").orNull?.toIntOrNull() ?: 1

/**
 * `versionName` : dérivé du tag quand la CI construit un tag `v*`, sinon la version de base.
 */
val baseVersionName = "0.1.0"
val buildVersionName: String = providers.environmentVariable("GITHUB_REF_NAME").orNull
    ?.takeIf { providers.environmentVariable("GITHUB_REF_TYPE").orNull == "tag" }
    ?.removePrefix("v")
    ?.takeIf { it.isNotBlank() }
    ?: baseVersionName

/**
 * Keystore de release fourni par les secrets du dépôt (voir README).
 * Absent → on retombe sur la clé de debug, pour qu'`assembleRelease` produise
 * un APK **installable** dès le premier run, sans rien configurer.
 */
val envKeystoreBase64: String? = providers.environmentVariable("KEYSTORE_BASE64").orNull
    ?.takeIf { it.isNotBlank() }
val envKeystorePassword: String? = providers.environmentVariable("KEYSTORE_PASSWORD").orNull
val envKeyAlias: String? = providers.environmentVariable("KEY_ALIAS").orNull
val envKeyPassword: String? = providers.environmentVariable("KEY_PASSWORD").orNull

val hasReleaseKeystore = envKeystoreBase64 != null &&
    !envKeystorePassword.isNullOrBlank() &&
    !envKeyAlias.isNullOrBlank() &&
    !envKeyPassword.isNullOrBlank()

android {
    namespace = "fr.bcolombani.bibli"

    // Compose 1.12, androidx.core 1.19 et okhttp-android 5.5 exigent une compilation
    // contre l'API 37 (leur `aar-metadata`). `targetSdk` reste 36 : c'est lui qui décide
    // du comportement d'exécution, et rien dans l'application ne demande l'API 37.
    compileSdk = 37

    defaultConfig {
        applicationId = "fr.bcolombani.bibli"
        minSdk = 26
        targetSdk = 36
        versionCode = buildVersionCode
        versionName = buildVersionName
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                val keystoreFile = layout.buildDirectory.file("keystore/release.jks").get().asFile
                keystoreFile.parentFile.mkdirs()
                keystoreFile.writeBytes(Base64.getDecoder().decode(envKeystoreBase64!!.trim()))
                storeFile = keystoreFile
                storePassword = envKeystorePassword
                keyAlias = envKeyAlias
                keyPassword = envKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            // v1 : R8 désactivé. ML Kit + kotlinx.serialization + Room sous R8 sans règles
            // vérifiées donnent une CI verte et une application cassée à l'exécution.
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        // Le `jvmTarget` du Kotlin intégré à AGP suit `targetCompatibility`.
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = false
            isReturnDefaultValues = true
        }
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
        // Le rapport HTML/XML est publié en artifact par la CI.
        htmlReport = true
        xmlReport = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE*",
                "/META-INF/NOTICE*",
            )
        }
    }
}

// Schémas Room versionnés et commités : indispensable pour écrire des migrations
// quand le modèle évoluera (couverture, éditeur, année, tags…).
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode.scanning)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kxml2)
}
