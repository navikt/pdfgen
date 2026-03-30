package no.nav.pdfgen.application.pdf

import com.openhtmltopdf.pdfboxout.PDFontSupplier
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import com.openhtmltopdf.svgsupport.BatikSVGDrawer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicReference
import no.nav.pdfgen.core.Environment
import no.nav.pdfgen.core.PDFGenCore
import no.nav.pdfgen.core.util.FontMetadata
import no.nav.pdfgen.logger
import org.apache.fontbox.ttf.TTFParser
import org.apache.pdfbox.io.RandomAccessReadBuffer
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.verapdf.pdfa.Foundries
import org.verapdf.pdfa.flavours.PDFAFlavour
import org.verapdf.pdfa.results.TestAssertion

private class CachedFontBytes(val metadata: FontMetadata, val bytes: ByteArray)

private val fontCache = AtomicReference<Pair<Environment, List<CachedFontBytes>>>()
private val fontCacheLock = Any()

private fun loadCachedFonts(): List<CachedFontBytes> {
    val env = PDFGenCore.environment
    fontCache.get()?.let { if (it.first === env) return it.second }
    return synchronized(fontCacheLock) {
        fontCache.get()?.let { if (it.first === env) return it.second }
        val fonts = env.fonts.map { CachedFontBytes(it, env.fontsRoot.readAllBytes(it.path)) }
        fontCache.set(env to fonts)
        fonts
    }
}

fun createPDFAWithCache(html: String): ByteArray {
    val fonts = loadCachedFonts()
    val env = PDFGenCore.environment
    val pdf =
        ByteArrayOutputStream()
            .apply {
                PdfRendererBuilder()
                    .apply {
                        for (cachedFont in fonts) {
                            val ttf =
                                TTFParser()
                                    .parse(RandomAccessReadBuffer(cachedFont.bytes))
                                    .also { it.isEnableGsub = false }
                            useFont(
                                PDFontSupplier(
                                    PDType0Font.load(PDDocument(), ttf, cachedFont.metadata.subset)
                                ),
                                cachedFont.metadata.family,
                                cachedFont.metadata.weight,
                                cachedFont.metadata.style,
                                cachedFont.metadata.subset,
                            )
                        }
                    }
                    .usePdfAConformance(PdfRendererBuilder.PdfAConformance.PDFA_2_A)
                    .usePdfUaAccessibility(true)
                    .useColorProfile(env.colorProfile)
                    .useSVGDrawer(BatikSVGDrawer())
                    .withHtmlContent(html, null)
                    .toStream(this)
                    .run()
            }
            .toByteArray()
    require(verifyCompliance(pdf)) { "Non-compliant PDF/A :(" }
    return pdf
}

private fun verifyCompliance(
    input: ByteArray,
    flavour: PDFAFlavour = PDFAFlavour.PDFA_2_A,
): Boolean {
    val pdf = ByteArrayInputStream(input)
    val validator = Foundries.defaultInstance().createValidator(flavour, false)
    val result = Foundries.defaultInstance().createParser(pdf).use { validator.validate(it) }
    val failures = result.testAssertions.filter { it.status != TestAssertion.Status.PASSED }
    failures.forEach { test ->
        logger.warn(test.message)
        logger.warn("Location ${test.location.context} ${test.location.level}")
        logger.warn("Status ${test.status}")
        logger.warn("Test number ${test.ruleId.testNumber}")
    }
    return failures.isEmpty()
}
