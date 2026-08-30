package fr.bcolombani.bibli.core.isbn

/**
 * Nettoyage des codes-barres lus : on ne garde que les caractères utiles à un ISBN
 * (chiffres et la clé de contrôle `X` d'un ISBN-10), et on convertit un ISBN-10 en ISBN-13.
 *
 * Kotlin pur, aucune dépendance Android : entièrement testable en JVM.
 */
object IsbnNormalizer {

    /** Supprime tirets, espaces (y compris insécables) et met la clé `x` en majuscule. */
    fun clean(raw: String): String = buildString(raw.length) {
        for (c in raw) {
            when {
                c in '0'..'9' -> append(c)
                c == 'X' || c == 'x' -> append('X')
                // tirets (ASCII et Unicode), espaces (y compris insécables) : ignorés
                c == '-' || c == '\u2010' || c == '\u2011' || c == '\u2012' ||
                    c == '\u2013' || c == '\u2014' || c == '\u00A0' || c == '\u202F' ||
                    c == '\uFEFF' || c.isWhitespace() -> Unit
                // tout autre caractère rend le code non-ISBN : on le conserve pour invalider
                else -> append(c)
            }
        }
    }

    /** Clé de contrôle d'un ISBN-13 / EAN-13 à partir des 12 premiers chiffres (poids 1/3 alternés). */
    fun checkDigit13(first12: String): Int {
        var sum = 0
        for (i in first12.indices) {
            val d = first12[i] - '0'
            sum += if (i % 2 == 0) d else d * 3
        }
        return (10 - sum % 10) % 10
    }

    /** Clé de contrôle d'un ISBN-10 à partir des 9 premiers chiffres (mod 11, `X` = 10). */
    fun checkDigit10(first9: String): Char {
        var sum = 0
        for (i in first9.indices) {
            sum += (first9[i] - '0') * (10 - i)
        }
        val rest = (11 - sum % 11) % 11
        return if (rest == 10) 'X' else ('0' + rest)
    }

    /** Convertit un ISBN-10 **déjà validé** en ISBN-13 préfixé `978`, clé recalculée. */
    fun isbn10To13(isbn10: String): String {
        val body = "978" + isbn10.substring(0, 9)
        return body + checkDigit13(body)
    }
}

/** Issue de l'analyse d'un code-barres. */
sealed interface IsbnCheck {
    /** Code reconnu comme ISBN : [isbn13] est la forme normalisée à 13 chiffres. */
    data class Valid(val isbn13: String) : IsbnCheck

    /** Code lu mais qui n'est pas un ISBN exploitable. */
    data class Invalid(val reason: Reason) : IsbnCheck

    enum class Reason {
        /** Longueur inattendue (EAN-8, QR, Code 128 arbitraire…). */
        BAD_LENGTH,

        /** Caractère non numérique à un endroit interdit. */
        BAD_CHARACTER,

        /** EAN-13 valide mais hors plage ISBN (préfixe ni 978 ni 979) : produit alimentaire, etc. */
        NOT_BOOKLAND,

        /** Préfixe 9790 : ISMN (partition musicale), pas un livre. */
        ISMN,

        /** Somme de contrôle fausse (code mal lu ou inventé). */
        BAD_CHECKSUM,
    }
}

/**
 * Valide et normalise un code-barres vers un ISBN-13.
 *
 * Règles :
 *  1. nettoyage (tirets / espaces) ;
 *  2. ISBN-13 / EAN-13 : 13 chiffres, préfixe `978` ou `979`, checksum mod 10 —
 *     **sauf** `9790…` qui est un ISMN et non un ISBN ;
 *  3. ISBN-10 : 10 caractères (dernier éventuellement `X`), checksum mod 11,
 *     puis conversion en ISBN-13 ;
 *  4. tout le reste est invalide.
 */
object IsbnValidator {

    fun check(raw: String): IsbnCheck {
        val cleaned = IsbnNormalizer.clean(raw)
        return when (cleaned.length) {
            13 -> check13(cleaned)
            10 -> check10(cleaned)
            else -> IsbnCheck.Invalid(IsbnCheck.Reason.BAD_LENGTH)
        }
    }

    /** Raccourci : l'ISBN-13 normalisé, ou `null` si le code n'est pas un ISBN. */
    fun normalizeOrNull(raw: String): String? = (check(raw) as? IsbnCheck.Valid)?.isbn13

    fun isValid(raw: String): Boolean = check(raw) is IsbnCheck.Valid

    private fun check13(s: String): IsbnCheck {
        if (!s.all { it in '0'..'9' }) return IsbnCheck.Invalid(IsbnCheck.Reason.BAD_CHARACTER)
        val prefix3 = s.substring(0, 3)
        if (prefix3 != "978" && prefix3 != "979") {
            return IsbnCheck.Invalid(IsbnCheck.Reason.NOT_BOOKLAND)
        }
        // 979-0 est la plage ISMN (partitions), volontairement exclue.
        if (s.startsWith("9790")) return IsbnCheck.Invalid(IsbnCheck.Reason.ISMN)
        if (IsbnNormalizer.checkDigit13(s.substring(0, 12)) != s[12] - '0') {
            return IsbnCheck.Invalid(IsbnCheck.Reason.BAD_CHECKSUM)
        }
        return IsbnCheck.Valid(s)
    }

    private fun check10(s: String): IsbnCheck {
        if (!s.substring(0, 9).all { it in '0'..'9' }) {
            return IsbnCheck.Invalid(IsbnCheck.Reason.BAD_CHARACTER)
        }
        val last = s[9]
        if (last !in '0'..'9' && last != 'X') return IsbnCheck.Invalid(IsbnCheck.Reason.BAD_CHARACTER)
        if (IsbnNormalizer.checkDigit10(s.substring(0, 9)) != last) {
            return IsbnCheck.Invalid(IsbnCheck.Reason.BAD_CHECKSUM)
        }
        return IsbnCheck.Valid(IsbnNormalizer.isbn10To13(s))
    }
}
