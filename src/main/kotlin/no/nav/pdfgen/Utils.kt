package no.nav.pdfgen

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import com.openhtmltopdf.svgsupport.BatikSVGDrawer
import java.awt.geom.AffineTransform
import java.awt.image.AffineTransformOp
import java.awt.image.AffineTransformOp.TYPE_BILINEAR
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import javax.imageio.ImageIO
import org.apache.pdfbox.io.IOUtils
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.apache.pdfbox.util.Matrix
import org.w3c.dom.Document

class Utils

val colorProfile: ByteArray = IOUtils.toByteArray(Utils::class.java.getResourceAsStream("/sRGB2014.icc"))

data class FontMetadata(
    val family: String,
    val path: String,
    val weight: Int,
    val style: BaseRendererBuilder.FontStyle,
    val subset: Boolean
) {
    val bytes: ByteArray = Files.readAllBytes(fontsRoot.resolve(path))
}

fun createPDFA(w3doc: Document, outputStream: OutputStream) = PdfRendererBuilder()
        .apply {
            for (font in fonts) {
                useFont({ ByteArrayInputStream(font.bytes) }, font.family, font.weight, font.style, font.subset)
            }
        }
        // .useFastMode() wait with fast mode until it doesn't print a bunch of errors
        .useColorProfile(colorProfile)
        .useSVGDrawer(BatikSVGDrawer())
        .usePdfAConformance(PdfRendererBuilder.PdfAConformance.PDFA_2_U)
        .withW3cDocument(w3doc, "")
        .toStream(outputStream)
        .buildPdfRenderer()
        .createPDF()

fun createPDFA(imageStream: InputStream, outputStream: OutputStream) {
    PDDocument().use { document ->

        val page = PDPage(PDRectangle.A4)
        document.addPage(page)
        val image = toPortait(ImageIO.read(imageStream))

        val quality = 1.0f

        val pdImage = JPEGFactory.createFromImage(document, image, quality)
        val imageSize = scale(pdImage, page)

        PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, false).use {
            it.drawImage(pdImage, Matrix(imageSize.width, 0f, 0f, imageSize.height, 0f, 0f))
        }

        document.save(outputStream)
    }
}

private fun toPortait(image: BufferedImage): BufferedImage {
    if (image.height >= image.width) {
        return image
    }

    val rotateTransform = AffineTransform.getRotateInstance(Math.toRadians(90.0), (image.height / 2f).toDouble(), (image.height / 2f).toDouble())

    return AffineTransformOp(rotateTransform, TYPE_BILINEAR)
            .filter(image, BufferedImage(image.height, image.width, image.type))
}

data class ImageSize(val width: Float, val height: Float)

private fun scale(image: PDImageXObject, page: PDPage): ImageSize {
    var width = image.width.toFloat()
    var height = image.height.toFloat()

    if (width > page.cropBox.width) {
        width = page.cropBox.width
        height = width * image.height.toFloat() / image.width.toFloat()
    }

    if (height > page.cropBox.height) {
        height = page.cropBox.height
        width = height * image.width.toFloat() / image.height.toFloat()
    }

    return ImageSize(width, height)
}
