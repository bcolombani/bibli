package fr.bcolombani.bibli.core.metadata

/** Assemble titre + sous-titre ; renvoie `null` si le titre est vide (réponse inexploitable). */
internal fun buildTitle(title: String?, subtitle: String?): String? {
    val main = title?.trim().orEmpty()
    if (main.isEmpty()) return null
    val sub = subtitle?.trim().orEmpty()
    return if (sub.isEmpty()) main else "$main : $sub"
}

/** Aplatit une liste d'auteurs selon la convention de la v1 : séparateur `", "`. */
internal fun List<String>.joinAuthors(): String =
    asSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .joinToString(", ")
