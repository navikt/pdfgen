package no.nav.pdfgen



val testTemplateIncludedFonts: String = getResource("/html/test_template_included_fonts.html")
val testTemplateInvalidFonts: String = getResource("/html/test_template_invalid_fonts.html")

val testJpg: ByteArray = getResource("/image/test.jpg")
val testPng: ByteArray = getResource("/image/test.png")

inline fun <reified T> getResource(path: String): T =
    PdfGenITest::class.java.getResourceAsStream(path).use { stream ->
        when (T::class) {
            String::class -> stream.bufferedReader(Charsets.UTF_8).use { it.readText() } as T
            ByteArray::class -> stream.readBytes() as T
            else -> throw UnsupportedOperationException()
        }
    }
