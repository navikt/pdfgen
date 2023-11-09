package no.nav.pdfgen.core

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.pdfgen.core.util.FontMetadata
import org.apache.pdfbox.io.IOUtils
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import kotlin.io.path.exists
import kotlin.io.path.extension

val objectMapper: ObjectMapper = ObjectMapper().findAndRegisterModules()
val templateRoot: Path = getFromEnvOrDefault("TEMPLATES_PATH", "templates/")
val imagesRoot: Path = getFromEnvOrDefault("RESOURCES_PATH", "resources/")
val fontsRoot: Path = getFromEnvOrDefault("FONTS_PATH", "fonts/")
class PDFgen {
    companion object {
        private var environment = Environment()
        fun getEnvironment() = environment
        fun init(environment: Environment) {
            PDFgen.environment = environment
        }
    }
}
data class Environment(
    val images: Map<String, String> = loadImages(),
    val resources: Map<String, ByteArray> = loadResources(),
    val colorProfile: ByteArray =
        IOUtils.toByteArray(Environment::class.java.getResourceAsStream("/sRGB2014.icc")),
    val fonts: List<FontMetadata> =
        objectMapper.readValue(Files.newInputStream(fontsRoot.resolve("config.json"))),
    val disablePdfGet: Boolean = System.getenv("DISABLE_PDF_GET")?.let { it == "true" } ?: false,
    val enableHtmlEndpoint: Boolean =
        System.getenv("ENABLE_HTML_ENDPOINT")?.let { it == "true" } ?: false,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Environment

        return colorProfile.contentEquals(other.colorProfile)
    }

    override fun hashCode(): Int {
        return colorProfile.contentHashCode()
    }
}

private fun getFromEnvOrDefault(envVariableName: String, defaultPath: String): Path {
    return System.getenv(envVariableName)?.let { Paths.get(it) } ?: run {
        val fromDefaultPath = Paths.get(defaultPath)
        return if (fromDefaultPath.exists()) fromDefaultPath else Paths.get(ClassLoader.getSystemResource(defaultPath).file.toString())
    }
}

private fun loadImages() =
    Files.list(imagesRoot)
        .filter {
            val validExtensions = setOf("jpg", "jpeg", "png", "bmp", "svg")
            !Files.isHidden(it) && it.fileName.extension in validExtensions
        }
        .map {
            val fileName = it.fileName.toString()
            val extension =
                when (it.fileName.extension) {
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

private fun loadResources() =
    Files.list(imagesRoot)
        .filter {
            val validExtensions = setOf("svg")
            !Files.isHidden(it) && it.fileName.extension in validExtensions
        }
        .map { it.fileName.toString() to Files.readAllBytes(it) }
        .toList()
        .toMap()
