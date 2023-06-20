package no.nav.pdfgen.util

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder
import java.nio.file.Files
import no.nav.pdfgen.fontsRoot

data class FontMetadata(
    val family: String,
    val path: String,
    val weight: Int,
    val style: BaseRendererBuilder.FontStyle,
    val subset: Boolean,
) {
    val bytes: ByteArray = Files.readAllBytes(fontsRoot.resolve(path))
}
