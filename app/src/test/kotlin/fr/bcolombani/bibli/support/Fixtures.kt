package fr.bcolombani.bibli.support

/** Charge une fixture depuis `src/test/resources`. */
object Fixtures {
    fun read(name: String): String =
        checkNotNull(Fixtures::class.java.classLoader?.getResourceAsStream(name)) {
            "fixture introuvable : $name"
        }.bufferedReader().use { it.readText() }
}
