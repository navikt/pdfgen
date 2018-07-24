package no.nav.pdfgen

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import org.apache.pdfbox.io.IOUtils
import org.apache.pdfbox.pdmodel.common.PDMetadata
import org.apache.pdfbox.pdmodel.graphics.color.PDOutputIntent
import org.apache.xmpbox.XMPMetadata
import org.apache.xmpbox.xml.XmpSerializer
import org.w3c.dom.Document
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class Utils

val colorProfile: ByteArray = IOUtils.toByteArray(Utils::class.java.getResourceAsStream("/sRGB2014.icc"))
val sourceSansProRegular: ByteArray = IOUtils.toByteArray(Utils::class.java.getResourceAsStream("/fonts/SourceSansPro-Regular.ttf"))
val sourceSansProBold: ByteArray = IOUtils.toByteArray(Utils::class.java.getResourceAsStream("/fonts/SourceSansPro-Bold.ttf"))

fun createPDFA(w3doc: Document, title: String): ByteArray {
    PdfRendererBuilder()
            .useFont({ ByteArrayInputStream(sourceSansProRegular) }, "Source Sans Pro",
                    400, BaseRendererBuilder.FontStyle.NORMAL, false)
            .useFont({ ByteArrayInputStream(sourceSansProBold) }, "Source Sans Pro", 700,
                    BaseRendererBuilder.FontStyle.NORMAL, false)
            // .useFastMode() wait with fast mode until it doesn't print a bunch of errors
            .withW3cDocument(w3doc, "")
            .buildPdfRenderer().use {
                renderer ->
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
            part = 2
        }
    }
    val serializer = XmpSerializer()
    return ByteArrayOutputStream().use {
        serializer.serialize(xmp, it, true)
        it.toByteArray()
    }
}
