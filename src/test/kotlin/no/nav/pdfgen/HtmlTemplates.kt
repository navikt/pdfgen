package no.nav.pdfgen

import org.apache.pdfbox.io.IOUtils

val testTemplateIncludedFonts: String = IOUtils.toByteArray(PdfGenITSpek::class.java.getResourceAsStream("/html/test_template_included_fonts.html")).toString(Charsets.UTF_8)
val testTemplateInvalidFonts: String = IOUtils.toByteArray(PdfGenITSpek::class.java.getResourceAsStream("/html/test_template_invalid_fonts.html")).toString(Charsets.UTF_8)
