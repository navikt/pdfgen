package no.nav.pdfgen

import org.apache.pdfbox.pdmodel.PDPageContentStream
import java.awt.Color

class Table(
    val border: Border? = null,
    val borderRight: Border? = border,
    val borderLeft: Border? = border,
    val borderTop: Border? = border,
    val borderBottom: Border? = border,
    val child: Renderable? = null,
    val height: Float = child?.calculateHeight() ?: 0f + 20
) : Renderable {
    override fun render(contentStream: PDPageContentStream) {
        contentStream.setNonStrokingColor(Color.BLACK)
        contentStream.setLineWidth(1f)
        contentStream.addRect(200f, 650f, 100f, height)
        contentStream.setLineDashPattern(floatArrayOf(2f), 2f)
        contentStream.stroke()
    }

    override fun calculateHeight(): Float {
        return height
    }
}
