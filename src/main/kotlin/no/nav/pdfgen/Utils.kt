package no.nav.pdfgen

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import com.openhtmltopdf.svgsupport.BatikSVGDrawer
import org.apache.pdfbox.io.IOUtils
import org.w3c.dom.Document
import java.io.ByteArrayInputStream
import java.io.OutputStream

class Utils

val colorProfile: ByteArray = IOUtils.toByteArray(Utils::class.java.getResourceAsStream("/sRGB2014.icc"))
val sourceSansProRegular: ByteArray = IOUtils.toByteArray(Utils::class.java.getResourceAsStream("/fonts/SourceSansPro-Regular.ttf"))
val sourceSansProBold: ByteArray = IOUtils.toByteArray(Utils::class.java.getResourceAsStream("/fonts/SourceSansPro-Bold.ttf"))

fun createPDFA(w3doc: Document, outputStream: OutputStream) {

    PdfRendererBuilder()
            .usePdfAConformance(PdfRendererBuilder.PdfAConformance.PDFA_2_U)
            .useColorProfile(colorProfile)
            .useSVGDrawer(BatikSVGDrawer())
            .useFont({ ByteArrayInputStream(sourceSansProRegular) }, "Source Sans Pro",
                    400, BaseRendererBuilder.FontStyle.NORMAL, false)
            .useFont({ ByteArrayInputStream(sourceSansProBold) }, "Source Sans Pro", 700,
                    BaseRendererBuilder.FontStyle.NORMAL, false)
            // .useFastMode() wait with fast mode until it doesn't print a bunch of errors
            .withW3cDocument(w3doc, "")
            .toStream(outputStream).buildPdfRenderer().createPDF()
}
