package no.nav.pdfgen

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import org.apache.pdfbox.pdmodel.common.PDMetadata
import org.apache.pdfbox.pdmodel.graphics.color.PDOutputIntent
import org.apache.xmpbox.XMPMetadata
import org.apache.xmpbox.xml.XmpSerializer
import org.w3c.dom.Document
import java.io.ByteArrayOutputStream
import java.io.File

fun createPDFA(w3doc: Document, title: String): ByteArrayOutputStream {
    val builder = PdfRendererBuilder().withW3cDocument(w3doc, "")
    val renderer = builder.buildPdfRenderer()
    renderer.createPDFWithoutClosing()
    val document = renderer.pdfDocument
    val metadata = PDMetadata(document)
    metadata.importXMPMetadata(createXMPMetadata(title))
    document.documentCatalog.metadata = metadata

    val colorProfile = File("resources/sRGB2014.icc").inputStream()
    val intent = PDOutputIntent(document, colorProfile)
    intent.info = "sRGB IEC61966-2.1"
    intent.outputCondition = "sRGB IEC61966-2.1"
    intent.outputConditionIdentifier = "sRGB IEC61966-2.1"
    intent.registryName = "http://www.color.org"
    document.documentCatalog.addOutputIntent(intent)
    val baos = ByteArrayOutputStream()
    document.save(baos)
    return baos
}

fun createXMPMetadata(title: String): ByteArray? {
    val xmp = XMPMetadata.createXMPMetadata()
    val dc = xmp.createAndAddDublinCoreSchema()
    dc.title = title
    dc.addCreator("pdfgen")
    val id = xmp.createAndAddPFAIdentificationSchema()
    id.part = 3
    id.conformance = "B"
    val serializer = XmpSerializer()
    val baos = ByteArrayOutputStream()
    serializer.serialize(xmp, baos, true)
    return baos.toByteArray()
}