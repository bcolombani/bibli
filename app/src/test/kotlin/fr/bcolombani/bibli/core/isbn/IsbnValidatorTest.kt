package fr.bcolombani.bibli.core.isbn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IsbnValidatorTest {

    // --- ISBN-13 valides -------------------------------------------------------------------

    @Test
    fun `ISBN-13 en 978 valide`() {
        assertEquals("9782070360024", IsbnValidator.normalizeOrNull("9782070360024"))
    }

    @Test
    fun `ISBN-13 en 979-10 valide (editeur francais)`() {
        // 979-10-235-XXXX : plage attribuée à la France depuis 2014.
        assertEquals("9791023505801", IsbnValidator.normalizeOrNull("9791023505801"))
    }

    @Test
    fun `ISBN-13 avec tirets et espaces`() {
        assertEquals("9782070360024", IsbnValidator.normalizeOrNull("978-2-07-036002-4"))
        assertEquals("9782070360024", IsbnValidator.normalizeOrNull("  978 2 07 036002 4  "))
        assertEquals("9782070360024", IsbnValidator.normalizeOrNull("978‑2‑07‑036002‑4"))
    }

    // --- ISBN-13 invalides -----------------------------------------------------------------

    @Test
    fun `ISBN-13 avec checksum faux`() {
        val check = IsbnValidator.check("9782070360025")
        assertEquals(IsbnCheck.Invalid(IsbnCheck.Reason.BAD_CHECKSUM), check)
        assertNull(IsbnValidator.normalizeOrNull("9782070360025"))
    }

    @Test
    fun `EAN-13 produit alimentaire rejete`() {
        // 3017620422003 : pâte à tartiner. Checksum correct, mais préfixe hors Bookland.
        assertEquals(
            IsbnCheck.Invalid(IsbnCheck.Reason.NOT_BOOKLAND),
            IsbnValidator.check("3017620422003"),
        )
    }

    @Test
    fun `ISMN en 9790 rejete`() {
        // 9790006134540 : partition musicale (ISMN), pas un livre.
        assertEquals(IsbnCheck.Invalid(IsbnCheck.Reason.ISMN), IsbnValidator.check("9790006134540"))
        assertFalse(IsbnValidator.isValid("979-0-006-13454-0"))
    }

    @Test
    fun `EAN-8 rejete`() {
        assertEquals(IsbnCheck.Invalid(IsbnCheck.Reason.BAD_LENGTH), IsbnValidator.check("96385074"))
    }

    @Test
    fun `contenu de QR code rejete`() {
        assertEquals(
            IsbnCheck.Invalid(IsbnCheck.Reason.BAD_LENGTH),
            IsbnValidator.check("https://example.org/livre"),
        )
    }

    @Test
    fun `code de 13 caracteres non numeriques rejete`() {
        assertEquals(
            IsbnCheck.Invalid(IsbnCheck.Reason.BAD_CHARACTER),
            IsbnValidator.check("ABCDEFGHIJKLM"),
        )
    }

    // --- ISBN-10 ---------------------------------------------------------------------------

    @Test
    fun `ISBN-10 converti en ISBN-13`() {
        assertEquals("9782070360024", IsbnValidator.normalizeOrNull("2070360024"))
    }

    @Test
    fun `ISBN-10 avec cle X`() {
        // 0-8044-2957-X : ISBN-10 classique dont la clé de contrôle vaut 10.
        assertEquals("9780804429573", IsbnValidator.normalizeOrNull("080442957X"))
        assertEquals("9780804429573", IsbnValidator.normalizeOrNull("0-8044-2957-x"))
    }

    @Test
    fun `ISBN-10 avec checksum faux`() {
        assertEquals(
            IsbnCheck.Invalid(IsbnCheck.Reason.BAD_CHECKSUM),
            IsbnValidator.check("2070360025"),
        )
    }

    @Test
    fun `ISBN-10 avec caractere illegal`() {
        assertEquals(
            IsbnCheck.Invalid(IsbnCheck.Reason.BAD_CHARACTER),
            IsbnValidator.check("20703600Z4"),
        )
    }

    // --- primitives ------------------------------------------------------------------------

    @Test
    fun `cles de controle`() {
        assertEquals(4, IsbnNormalizer.checkDigit13("978207036002"))
        assertEquals('4', IsbnNormalizer.checkDigit10("207036002"))
        assertEquals('X', IsbnNormalizer.checkDigit10("080442957"))
        assertEquals("9782070360024", IsbnNormalizer.isbn10To13("2070360024"))
    }

    @Test
    fun `nettoyage`() {
        assertEquals("9782070360024", IsbnNormalizer.clean("978-2-07-036002-4"))
        assertEquals("080442957X", IsbnNormalizer.clean("0 8044 2957 x"))
        assertTrue(IsbnNormalizer.clean("https://x.y").contains("https"))
    }
}
