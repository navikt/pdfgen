package no.nav.pdfgen.core.pdf

import java.io.ByteArrayOutputStream
import java.io.InputStream

fun createPdfFromImage(inputStream: InputStream): ByteArray =
    ByteArrayOutputStream().use { outputStream ->
        createPDFA(inputStream, outputStream)
        outputStream.toByteArray()
    }
