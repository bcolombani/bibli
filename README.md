# Bibli — inventorier sa bibliothèque au scanner

[![build](https://github.com/bcolombani/bibli/actions/workflows/build.yml/badge.svg)](https://github.com/bcolombani/bibli/actions/workflows/build.yml)

Application Android native pour inventorier une bibliothèque personnelle en scannant
les codes-barres ISBN des livres, **en chaîne**, sans toucher l'écran entre deux livres.

## Ce que fait l'application

- **Elle démarre directement en mode scan**, caméra active, écran maintenu allumé.
- Chaque livre reconnu est enregistré localement, avec son titre, ses auteurs et
  **la source d'où vient la fiche**.
- Les métadonnées sont cherchées dans trois catalogues successifs, en s'arrêtant au
  premier qui répond : **Google Books**, puis **Open Library**, puis le **catalogue de
  la BnF** (qui rattrape une grande partie des livres français absents des deux autres).
- Tout est local : pas de compte, pas de backend, aucune clé d'API.

### Les quatre issues d'un scan

| Cas | Retour | Suite |
|---|---|---|
| ISBN valide, trouvé par une API | double bip montant, **coche verte** | enregistré, retour au scan immédiat |
| ISBN valide, aucune API ne trouve | bip simple, **warning orange** | feuille de saisie manuelle (titre / auteur), puis retour au scan |
| Code-barres lu mais pas un ISBN | bip d'erreur, **croix rouge** | rien n'est enregistré, retour au scan immédiat |
| ISBN déjà présent en base | double bip court, **coche bleue** | pas de doublon, retour au scan immédiat |

Le détail complet du parcours d'un livre, du code-barres à la fiche, est décrit dans
[`docs/processus-scan.md`](docs/processus-scan.md) (avec un schéma).

L'icône est **toujours affichée** : elle est le canal principal, le son n'est qu'un bonus.
Un même code relu dans les 3 secondes est ignoré, et la caméra cesse d'analyser tant que
la feuille de saisie manuelle est ouverte.

### Bibliothèque

Liste alimentée directement par la base (elle se met à jour toute seule après chaque
scan), recherche insensible à la casse **et aux accents** avec portée `Tout` / `Titre` /
`Auteur` / `ISBN`, tri par date d'ajout, titre ou auteur, badge de provenance sur chaque
ligne — celui de la **saisie manuelle** est volontairement le plus visible.

Appui simple sur une ligne : édition du titre et des auteurs (la fiche bascule alors sur
la source `Saisie manuelle`). Appui long : suppression, avec un « Annuler » dans la
Snackbar.

### Export JSON

Bouton d'export dans l'écran Bibliothèque : le système demande où écrire le fichier
(aucune permission de stockage n'est nécessaire), nom proposé
`bibliotheque-AAAAMMJJ-HHmm.json`.

```json
{
  "schemaVersion": 1,
  "exportedAt": "2026-08-30T14:12:00Z",
  "count": 128,
  "books": [
    {
      "isbn13": "9782070368228",
      "title": "L'Étranger",
      "authors": "Albert Camus",
      "source": "GOOGLE_BOOKS",
      "addedAt": "2026-08-30T13:58:11Z"
    }
  ]
}
```

`schemaVersion` existe pour permettre un import ultérieur ; **l'import n'est pas
implémenté en v1**.

## Installer l'APK

1. Ouvrir la **[page des Releases](https://github.com/bcolombani/bibli/releases)** et
   télécharger le fichier `bibli-vX.Y.Z.apk` de la dernière version.
   *(Les builds intermédiaires sont aussi disponibles en artifact `app-release-apk` sur
   [la page des exécutions CI](https://github.com/bcolombani/bibli/actions/workflows/build.yml).)*
2. Sur le téléphone, ouvrir le fichier téléchargé.
3. Android demande d'autoriser l'installation depuis cette source : **Paramètres →
   Applications → Accès spécial → Installer des applications inconnues**, choisir le
   navigateur ou le gestionnaire de fichiers utilisé, puis activer l'autorisation.
4. Revenir sur le fichier et installer.
5. Au premier lancement, accepter l'accès à l'appareil photo.

Le modèle de reconnaissance de codes-barres est **embarqué dans l'APK** (variante
« bundled » de ML Kit) : le scan fonctionne immédiatement, sans téléchargement au
premier lancement.

## Signature

Par défaut, `assembleRelease` signe l'APK avec la **clé de debug**. C'est ce qui permet
d'avoir un APK installable dès le premier build, sans rien configurer. Un tel APK est
parfait pour un usage personnel en sideload, mais ne peut pas être publié sur le Play
Store et ne permet pas de mise à jour par-dessus un APK signé autrement.

Pour passer à une vraie clé :

```bash
# 1. Créer le keystore (à conserver précieusement et hors du dépôt)
keytool -genkeypair -v \
  -keystore bibli-release.jks \
  -alias bibli \
  -keyalg RSA -keysize 4096 -validity 10000

# 2. L'encoder pour le stocker en secret GitHub
base64 -w0 bibli-release.jks > bibli-release.jks.base64
```

Puis dans **Settings → Secrets and variables → Actions** du dépôt, créer :

| Secret | Contenu |
|---|---|
| `KEYSTORE_BASE64` | le contenu de `bibli-release.jks.base64` |
| `KEYSTORE_PASSWORD` | mot de passe du keystore |
| `KEY_ALIAS` | `bibli` |
| `KEY_PASSWORD` | mot de passe de la clé |

Au prochain build, la présence des quatre variables suffit : le `signingConfig` bascule
automatiquement sur ce keystore. S'il en manque une seule, le repli sur la clé de debug
reste actif.

> Attention : passer de la clé de debug à une vraie clé change la signature de
> l'application. Il faudra désinstaller la version précédente avant d'installer la
> nouvelle.

## Développement

Voir [`CLAUDE.md`](CLAUDE.md) pour les conventions et [`PLAN.md`](PLAN.md) pour la
structure du projet.

```bash
./gradlew testDebugUnitTest    # tests unitaires JVM
./gradlew lintDebug            # lint Android
./gradlew assembleRelease      # APK
```

Publier une version : pousser un tag `vX.Y.Z`, la CI construit et attache l'APK à une
Release GitHub.

## Écarts à la spec

- **`compileSdk = 37`, `targetSdk = 36`.** La spec demandait 36 pour les deux, mais
  Compose 1.12, `androidx.core` 1.19 et `okhttp-android` 5.5 refusent d'être compilés
  contre l'API 36 (leur `aar-metadata` exige 37). Plutôt que de rétrograder une dizaine
  de dépendances vers des versions plus anciennes — ce que la spec demandait justement
  d'éviter — seul `compileSdk` monte à 37. `targetSdk` reste à 36 : c'est lui qui décide
  du comportement d'exécution de l'application, et c'était le sens de la consigne.
- **R8 / minification désactivés** en v1 (`isMinifyEnabled = false`), comme demandé.
  Les règles ProGuard nécessaires à ML Kit, Room et kotlinx.serialization sont déjà
  écrites dans `app/proguard-rules.pro` pour le jour où on les réactivera.
- **Suppression par appui long uniquement**, pas par balayage. La spec autorisait
  « appui long *ou* balayage » ; l'appui long évite de dépendre d'une API Compose encore
  mouvante, pour un résultat identique côté utilisateur.
- **Mise en pause de la caméra** pendant la saisie manuelle : c'est l'analyse d'images
  qui est détachée, pas la session caméra entière. Plus aucune image n'est traitée, mais
  on évite le clignotement d'un rebind complet à la fermeture de la feuille — ce qui
  compte quand on enchaîne les livres.
- **Tri du titre et de l'auteur** : la ponctuation est ignorée dans la clé de tri, sans
  quoi `L'Étranger` passerait avant `La Peste` (l'apostrophe précède les lettres dans
  l'ordre des points de code).
- **Développement sans SDK Android local** : l'environnement de développement n'avait
  ni SDK Android ni accès à Google Maven. Le cœur métier a été vérifié hors CI par
  45 tests JVM ; tout ce qui touche Android a été validé par la CI. Les numéros de
  version des dépendances ont été relevés sur les dépôts réels via un job CI dédié,
  et non écrits de mémoire.

## Licence

Voir [`LICENSE`](LICENSE).
