# CLAUDE.md — conventions du projet Bibli

Application Android d'inventaire de bibliothèque personnelle par scan de codes-barres ISBN.

## Stack

- Kotlin 2.3.21, Jetpack Compose (BOM 2026.08.00), Material 3 avec couleur dynamique.
- Module unique `:app`. `minSdk 26`, `compileSdk`/`targetSdk 36`, **JDK 17**.
- Gradle Kotlin DSL + version catalog (`gradle/libs.versions.toml`), wrapper commité.
- Room + KSP pour la persistance ; OkHttp + kotlinx.serialization pour le réseau ;
  `XmlPullParser` pour le XML de la BnF.
- **Pas de Retrofit, pas de Hilt, pas de Firebase.** L'injection se fait à la main dans
  `AppContainer`, construit par `BibliApplication`.

## Structure et règle d'or

```
core/   Kotlin pur. Aucune importation `android.*` ni `androidx.*`.
data/   Room et dépôt.
ui/     Compose, ViewModels.
```

**Toute logique testable va dans `core/`.** C'est ce qui permet de couvrir la validation
ISBN, le parsing des trois sources, la chaîne de repli, les filtres de liste et l'export
avec des tests JVM, sans émulateur.

Seule exception assumée dans `core/` : `BnfSruSource` manipule l'interface
`org.xmlpull.v1.XmlPullParser`. L'instance est **injectée** (`parserFactory`) précisément
pour que les tests puissent fournir une implémentation réelle (kXML 2), le `android.jar`
des tests unitaires ne contenant que des stubs.

## Où changer quoi

| Besoin | Endroit |
|---|---|
| Réordonner / ajouter une source de métadonnées | `AppContainer.metadataSources` (une liste, un seul endroit) |
| Modifier les budgets de temps réseau | `MetadataLookupChain.PER_SOURCE_TIMEOUT_MS` / `TOTAL_BUDGET_MS` |
| Modifier l'anti-rebond ou la durée de l'overlay | `ScanViewModel.DEBOUNCE_MS` / `OVERLAY_MS` |
| Ajouter un champ au modèle | `BookEntity` + migration Room + `Book` + `BookExportDto` |
| Changer sons / vibrations | `ScanFeedback` |
| Changer les formats de codes-barres lus | `BarcodeAnalyzer` |

## Tests

- **JVM uniquement.** Pas de test instrumenté, pas d'émulateur en CI.
- Lancement : `./gradlew testDebugUnitTest`.
- Les réponses d'API sont des **fixtures** dans `app/src/test/resources`, une par cas
  (trouvé / vide / malformé) et par source.
- La chaîne de repli se teste avec `okhttp3.mockwebserver`, les trois sources pointant
  sur des chemins distincts du même serveur.
- **Interdit** : désactiver, ignorer ou affaiblir un test pour faire passer la CI.
  Un test rouge signale soit un bug, soit une attente fausse — dans les deux cas
  on corrige la cause.

## Base de données

`exportSchema = true`, schémas commités dans `app/schemas/`. Toute évolution de
`BookEntity` impose de monter `version` et d'écrire une `Migration` : ne jamais
recourir à `fallbackToDestructiveMigration`, la bibliothèque de l'utilisateur est
la seule copie de son inventaire.

## Build et CI

```bash
./gradlew testDebugUnitTest          # tests unitaires
./gradlew lintDebug                  # lint Android
./gradlew assembleRelease            # APK installable (signé debug si pas de keystore)
```

- `.github/workflows/build.yml` : push/PR sur `main` + `workflow_dispatch`.
  Publie `app-release-apk` et les rapports de tests (`if: always()`).
- `.github/workflows/release.yml` : tag `v*` → Release GitHub avec l'APK attaché.
- `versionCode` = `GITHUB_RUN_NUMBER` (1 en local), `versionName` dérivé du tag.
- La signature de release lit `KEYSTORE_BASE64` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` /
  `KEY_PASSWORD` ; **si elles sont absentes, elle retombe sur la clé de debug** pour
  qu'`assembleRelease` produise toujours un APK installable.

## Conventions de commit

Conventional commits, en français : `feat:`, `fix:`, `ci:`, `docs:`, `test:`, `chore:`.
Un commit = un changement cohérent.

## Environnement de développement sans SDK Android

Si `ANDROID_HOME` est vide ou Google Maven injoignable, aucun build Android n'est
possible localement : la CI fait foi. Le cœur `core/` reste vérifiable hors ligne
dans un projet Gradle JVM temporaire qui ne compile que `core/**` (voir `PLAN.md`).
