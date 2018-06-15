package no.nav.pdfgen

import org.apache.pdfbox.pdmodel.PDPageContentStream

data class DocumentPage(
        val elements: List<Any>
)

interface Renderable {
    fun render(contentStream: PDPageContentStream)
    fun calculateHeight(): Float
}
