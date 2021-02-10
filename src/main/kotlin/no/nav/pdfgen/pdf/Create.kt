package no.nav.pdfgen.pdf

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import com.openhtmltopdf.svgsupport.BatikSVGDrawer
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import no.nav.pdfgen.Environment
import no.nav.pdfgen.api.render
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDMetadata
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDMarkInfo
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureTreeRoot
import org.apache.pdfbox.pdmodel.graphics.color.PDOutputIntent
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory
import org.apache.pdfbox.pdmodel.interactive.viewerpreferences.PDViewerPreferences
import org.apache.pdfbox.util.Matrix
import org.apache.xmpbox.XMPMetadata
import org.apache.xmpbox.type.BadFieldValueException
import org.apache.xmpbox.xml.XmpSerializer
import org.w3c.dom.Document
import no.nav.pdfgen.util.scale
import no.nav.pdfgen.util.toPortait
import java.io.*
import java.lang.IllegalArgumentException
import java.util.*
import javax.imageio.ImageIO

fun createPDFA(w3doc: Document, outputStream: OutputStream, env: Environment) {
    val renderer = PdfRendererBuilder()
        .apply {
            for (font in env.fonts) {
                useFont({ ByteArrayInputStream(font.bytes) }, font.family, font.weight, font.style, font.subset)
            }
        }
        .useSVGDrawer(BatikSVGDrawer())
        .withW3cDocument(w3doc, "")
        .buildPdfRenderer()

    renderer.createPDFWithoutClosing()
    renderer.pdfDocument.conform(env)
    renderer.pdfDocument.save(outputStream)
    renderer.pdfDocument.close()
}

fun createPDFA(imageStream: InputStream, outputStream: OutputStream, env: Environment) {
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
        document.conform(env)
        document.save(outputStream)
        document.close()
    }
}

fun PDDocument.conform(env: Environment) {
    val xmp = XMPMetadata.createXMPMetadata()
    val catalog = this.documentCatalog
    val cal = Calendar.getInstance()
    val page = PDPage(PDRectangle.A4)

    try {
        val dc = xmp.createAndAddDublinCoreSchema()
        dc.addCreator("pdfgen")
        dc.addDate(cal)

        val id = xmp.createAndAddPFAIdentificationSchema()
        id.part = 2
        id.conformance = "U"

        val serializer = XmpSerializer()
        val baos = ByteArrayOutputStream()
        serializer.serialize(xmp, baos, true)

        val metadata = PDMetadata(this)
        metadata.importXMPMetadata(baos.toByteArray())
        catalog.metadata = metadata
    } catch (e: BadFieldValueException) {
        throw IllegalArgumentException(e)
    }

    val intent = PDOutputIntent(this, env.colorProfile.inputStream())
    intent.info = "sRGB IEC61966-2.1"
    intent.outputCondition = "sRGB IEC61966-2.1"
    intent.outputConditionIdentifier = "sRGB IEC61966-2.1"
    intent.registryName = "http://www.color.org"
    catalog.addOutputIntent(intent)
    catalog.language = "nb-NO"

    val pdViewer = PDViewerPreferences(page.cosObject)
    pdViewer.setDisplayDocTitle(true)
    catalog.viewerPreferences = pdViewer

    catalog.markInfo = PDMarkInfo(page.cosObject)
    catalog.structureTreeRoot = PDStructureTreeRoot()
    catalog.markInfo.isMarked = true
}

class PdfContent(
    private val w3Doc: Document,
    private val env: Environment,
    override val contentType: ContentType = ContentType.Application.Pdf
) : OutgoingContent.WriteChannelContent() {
    override suspend fun writeTo(channel: ByteWriteChannel) {
        channel.toOutputStream().use {
            createPDFA(w3Doc, it, env)
        }
    }
}
