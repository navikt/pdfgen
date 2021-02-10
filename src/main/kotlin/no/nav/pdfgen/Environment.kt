package no.nav.pdfgen

import com.fasterxml.jackson.module.kotlin.readValue
import com.github.jknack.handlebars.Template
import io.ktor.util.*
import no.nav.pdfgen.template.loadTemplates
import no.nav.pdfgen.util.FontMetadata
import org.apache.pdfbox.io.IOUtils
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import kotlin.streams.toList

val templateRoot: Path = Paths.get("templates/")
val imagesRoot: Path = Paths.get("resources/")
val fontsRoot: Path = Paths.get("fonts/")


data class Environment(
    val images: Map<String, String> = loadImages(),
    val resources: Map<String, ByteArray> = loadResources(),
    val colorProfile: ByteArray = IOUtils.toByteArray(Environment::class.java.getResourceAsStream("/sRGB2014.icc")),
    val fonts: List<FontMetadata> = objectMapper.readValue(Files.newInputStream(fontsRoot.resolve("config.json"))),
    val disablePdfGet: Boolean = System.getenv("DISABLE_PDF_GET")?.let { it == "true" } ?: false
)


fun loadImages() = Files.list(imagesRoot)
    .filter {
        val validExtensions = setOf("jpg", "jpeg", "png", "bmp", "svg")
        !Files.isHidden(it) && it.fileName.extension in validExtensions
    }
    .map {
        val fileName = it.fileName.toString()
        val extension = when (it.fileName.extension) {
            "jpg" -> "jpeg" // jpg is not a valid mime-type
            "svg" -> "svg+xml"
            else -> it.fileName.extension
        }
        val base64string = Base64.getEncoder().encodeToString(Files.readAllBytes(it))
        val base64 = "data:image/$extension;base64,$base64string"
        fileName to base64
    }
    .toList()
    .toMap()

fun loadResources() = Files.list(imagesRoot)
    .filter {
        val validExtensions = setOf("svg")
        !Files.isHidden(it) && it.fileName.extension in validExtensions
    }
    .map {
        it.fileName.toString() to Files.readAllBytes(it)
    }
    .toList()
    .toMap()
