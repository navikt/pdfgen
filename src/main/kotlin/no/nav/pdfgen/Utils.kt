package no.nav.pdfgen

import com.openhtmltopdf.css.constants.IdentValue
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import org.apache.pdfbox.pdmodel.common.PDMetadata
import org.apache.pdfbox.pdmodel.graphics.color.PDOutputIntent
import org.apache.xmpbox.XMPMetadata
import org.apache.xmpbox.xml.XmpSerializer
import org.w3c.dom.Document
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Paths

class Utils

val colorProfile: ByteArray = Files.readAllBytes(Paths.get(Utils::class.java.getResource("/sRGB2014.icc").toURI()))

fun createPDFA(w3doc: Document, title: String): ByteArray {
    PdfRendererBuilder().withW3cDocument(w3doc, "").buildPdfRenderer().use {
        renderer ->
        renderer.fontResolver.addFont({
            Utils::class.java.getResourceAsStream("/fonts/SourceSansPro-Regular.ttf")
        }, "Source Sans Pro", IdentValue.NORMAL.FS_ID, IdentValue.NORMAL, false)
        renderer.fontResolver.addFont({
            Utils::class.java.getResourceAsStream("/fonts/SourceSansPro-Bold.ttf")
        }, "Source Sans Pro", IdentValue.BOLD.FS_ID, IdentValue.NORMAL, true)
        renderer.createPDFWithoutClosing()
        return renderer.pdfDocument.use {
            it.documentCatalog.metadata = PDMetadata(it).apply {
                importXMPMetadata(createXMPMetadata(title))
            }
            it.documentCatalog.addOutputIntent(PDOutputIntent(it, ByteArrayInputStream(colorProfile)).apply {
                info = "sRGB IEC61966-2.1"
                outputCondition = "sRGB IEC61966-2.1"
                outputConditionIdentifier = "sRGB IEC61966-2.1"
                registryName = "http://www.color.org"
            })
            ByteArrayOutputStream().use {
                bytesOut ->
                it.save(bytesOut)
                bytesOut.toByteArray()
            }
        }
    }
}

fun createXMPMetadata(t: String): ByteArray {
    val xmp = XMPMetadata.createXMPMetadata().apply {
        createAndAddDublinCoreSchema().apply {
            title = t
            addCreator("pdfgen")
        }
        createAndAddPFAIdentificationSchema().apply {
            conformance = "B"
            part = 3
        }
    }
    val serializer = XmpSerializer()
    return ByteArrayOutputStream().use {
        serializer.serialize(xmp, it, true)
        it.toByteArray()
    }
}
