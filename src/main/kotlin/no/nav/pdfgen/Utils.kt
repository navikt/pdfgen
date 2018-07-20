package no.nav.pdfgen

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import org.apache.pdfbox.pdmodel.common.PDMetadata
import org.apache.pdfbox.pdmodel.graphics.color.PDOutputIntent
import org.apache.xmpbox.XMPMetadata
import org.apache.xmpbox.xml.XmpSerializer
import org.w3c.dom.Document
import java.io.ByteArrayOutputStream

class Utils

fun createPDFA(w3doc: Document, title: String): ByteArrayOutputStream {
    val builder = PdfRendererBuilder().withW3cDocument(w3doc, "")
    val renderer = builder.buildPdfRenderer()
    val colorProfile = Utils::class.java.getResourceAsStream("resources/sRGB2014.icc")
    val baos = ByteArrayOutputStream()
    renderer.createPDFWithoutClosing()
    renderer.pdfDocument.use {
        it.documentCatalog.metadata = PDMetadata(it).apply {
            importXMPMetadata(createXMPMetadata(title))
        }
        it.documentCatalog.addOutputIntent(PDOutputIntent(it, colorProfile).apply {
            info = "sRGB IEC61966-2.1"
            outputCondition = "sRGB IEC61966-2.1"
            outputConditionIdentifier = "sRGB IEC61966-2.1"
            registryName = "http://www.color.org"
        })
        it.save(baos)
    }
    return baos
}

fun createXMPMetadata(t: String): ByteArray? {
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
