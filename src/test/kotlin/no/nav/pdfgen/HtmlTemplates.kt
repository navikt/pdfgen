package no.nav.pdfgen

import io.ktor.util.*

@KtorExperimentalAPI
val testTemplateIncludedFonts: String = getResource("/html/test_template_included_fonts.html")
@KtorExperimentalAPI
val testTemplateInvalidFonts: String = getResource("/html/test_template_invalid_fonts.html")

@KtorExperimentalAPI
val testJpg: ByteArray = getResource("/image/test.jpg")
@KtorExperimentalAPI
val testPng: ByteArray = getResource("/image/test.png")

@KtorExperimentalAPI
inline fun <reified T> getResource(path: String): T =
    PdfGenITSpek::class.java.getResourceAsStream(path).use { stream ->
        when (T::class) {
            String::class -> stream.bufferedReader(Charsets.UTF_8).use { it.readText() } as T
            ByteArray::class -> stream.readBytes() as T
            else -> throw UnsupportedOperationException()
        }
    }
