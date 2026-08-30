# Plan d'exécution — Bibli

## Contrainte d'environnement constatée au démarrage

Pas de SDK Android en local (`ANDROID_HOME` vide) et `dl.google.com` bloqué par la
politique réseau de la session : **aucun build Android n'est possible en local**.
En revanche Maven Central, le Gradle Plugin Portal et `services.gradle.org` sont
joignables.

D'où la méthode retenue :

1. **Sonder les versions réelles** via un job GitHub Actions jetable qui lit les
   `maven-metadata.xml` de Google Maven (le runner, lui, a le réseau complet), plutôt
   que de figer des numéros de version de mémoire.
2. **Vérifier tout le code « pur » en local** dans un projet Gradle JVM temporaire qui
   pointe sur `app/src/main/kotlin/**/core/**` : ISBN, parsing des trois sources,
   chaîne de repli, export JSON, filtres de liste. 45 tests tournent hors CI.
3. **Laisser la CI faire foi** pour tout ce qui touche Android (AGP, Room/KSP, Compose,
   CameraX, ML Kit, lint, APK).

## Arborescence

```
build.gradle.kts              plugins déclarés, aucun code
settings.gradle.kts           dépôts, module unique :app
gradle/libs.versions.toml     catalogue de versions
gradle/wrapper/               wrapper commité (Gradle 9.5.0), gradlew +x
app/
  build.gradle.kts            android {}, signature avec repli debug, KSP/Room
  proguard-rules.pro          prêt pour le jour où R8 sera réactivé
  schemas/                    schémas Room exportés et commités
  src/main/kotlin/fr/bcolombani/bibli/
    core/                     ← Kotlin pur, testable en JVM
      isbn/                   IsbnNormalizer, IsbnValidator, IsbnCheck
      http/                   HttpFetcher (OkHttp suspendu, annulable)
      metadata/               BookMetadataSource + 3 implémentations + chaîne de repli
      model/                  Book
      library/                filtres et tris de la liste
      export/                 schéma et sérialisation de l'export JSON
      scan/                   ScanProcessor : les 4 issues d'un scan
    data/db/                  BookEntity, BookDao, BibliDatabase (Room)
    data/repo/                BookRepository (implémente BookStore)
    ui/scan/                  CameraX + ML Kit, ViewModel, overlay, son/vibration
    ui/library/               liste, recherche, tri, édition, export SAF
    ui/theme|common/          Material 3 dynamique, badges de source
    AppContainer.kt           injection manuelle ; ordre des sources défini ici
  src/test/                   tests JVM + fixtures des trois sources
.github/workflows/build.yml   tests + lint + APK, artifacts
.github/workflows/release.yml tag v* → Release avec APK attaché
```

## Dépendances retenues

| Rôle | Choix |
|---|---|
| Build | AGP 9.3.2, Gradle 9.5.0, JDK 17, Kotlin 2.3.21 |
| Génération | KSP 2.3.11 (Room uniquement — pas de Hilt) |
| UI | Compose BOM 2026.08.00, Material 3, navigation-compose 2.10.0 |
| Persistance | Room 2.8.4, `exportSchema = true` |
| Caméra | CameraX 1.6.2 + ML Kit `barcode-scanning` 17.3.0 (**bundled**) |
| Réseau | OkHttp 5.5.0 + kotlinx.serialization 1.11.0, `XmlPullParser` pour la BnF |

## Ordre des étapes

1. Sondage des versions en CI. ✅
2. Cœur métier pur + tests, validés en local. ✅
3. Squelette Gradle : catalogue, wrapper, signature avec repli debug. ✅
4. Couche Android : Room, dépôt, conteneur d'injection. ✅
5. UI de scan : caméra, analyse, 4 issues, anti-rebond, saisie manuelle. ✅
6. UI liste : recherche/tri, édition, suppression annulable, export SAF. ✅
7. Workflows CI/CD + documentation. ✅
8. Itérations sur la CI jusqu'au vert, puis tag `v0.1.0`.
