package no.nav.pdfgen

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.jknack.handlebars.Handlebars
import com.github.jknack.handlebars.Helper
import com.github.jknack.handlebars.Template
import com.github.jknack.handlebars.Context
import com.github.jknack.handlebars.JsonNodeValueResolver
import com.github.jknack.handlebars.io.FileTemplateLoader
import com.github.jknack.handlebars.io.StringTemplateSource
import io.ktor.application.call
import io.ktor.content.OutgoingContent
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.request.receiveStream
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.response.respondWrite
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.extension
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.exporter.common.TextFormat
import kotlinx.coroutines.experimental.io.ByteWriteChannel
import kotlinx.coroutines.experimental.io.jvm.javaio.toOutputStream
import net.logstash.logback.argument.StructuredArguments.keyValue
import org.jsoup.Jsoup
import org.jsoup.helper.W3CDom
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.w3c.dom.Document
import java.nio.file.Files
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.Base64.Encoder
import kotlin.streams.toList

val collectorRegistry: CollectorRegistry = CollectorRegistry.defaultRegistry
val objectMapper: ObjectMapper = ObjectMapper()
val base64encoder: Encoder = Base64.getEncoder()
val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
val templateRoot: Path = Paths.get("templates/")
val imagesRoot: Path = Paths.get("resources/")
val images = loadImages()
val handlebars: Handlebars = Handlebars(FileTemplateLoader(templateRoot.toFile())).apply {
    infiniteLoops(true)

    registerHelpers(File("src/main/kotlin/no/nav/pdfgen/Helpers.js"))

    registerHelper("iso_to_nor_date", Helper<String> { context, _ ->
        if (context == null) return@Helper ""
        dateFormat.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(context))
    })
    registerHelper("iso_to_date", Helper<String> { context, _ ->
        if (context == null) return@Helper ""
        dateFormat.format(DateTimeFormatter.ISO_DATE.parse(context))
    })
    registerHelper("insert_at", Helper<Any> { context, options ->
        if (context == null) return@Helper ""
        val divider = options.hash<String>("divider", " ")
        options.params
                .map { it as Int }
                .fold(context.toString()) { v, idx -> v.substring(0, idx) + divider + v.substring(idx, v.length) }
    })

    registerHelper("eq", Helper<String> { context, options ->
        if (context == options.param(0)) options.fn() else options.inverse()
    })

    registerHelper("not_eq", Helper<String> { context, options ->
        if (context != options.param(0)) options.fn() else options.inverse()
    })

    registerHelper("safe", Helper<String> { context, _ ->
        if (context == null) "" else Handlebars.SafeString(context)
    })

    registerHelper("image", Helper<String> { context, _ ->
        if (context == null) "" else images[context]
    })

    registerHelper("capitalize", Helper<String> { context, _ ->
        if (context == null) "" else context.toLowerCase().capitalize()
    })
}
val log: Logger = LoggerFactory.getLogger("pdf-gen")

fun main(args: Array<String>) {
    initializeApplication(8080).start(wait = true)
}

class PdfContent(
        private val w3Doc: Document,
        private val title: String,
        override val contentType: ContentType = ContentType.parse("application/pdf")
) : OutgoingContent.WriteChannelContent() {
    override suspend fun writeTo(channel: ByteWriteChannel) {
        channel.toOutputStream().use {
            createPDFA(w3Doc, title, it)
        }
    }
}

fun initializeApplication(port: Int): ApplicationEngine {
    System.setProperty("sun.java2d.cmm", "sun.java2d.cmm.kcms.KcmsServiceProvider")
    val templates = loadTemplates()
    val disablePdfGet = System.getenv("DISABLE_PDF_GET")?.let { it == "true" } ?: false

    return embeddedServer(Netty, port) {
        routing {
            get("/is_ready") {
                call.respondText("I'm ready")
            }
            get("/is_alive") {
                call.respondText("I'm alive")
            }
            get("/prometheus") {
                val names = call.request.queryParameters.getAll("name[]")?.toSet() ?: setOf()
                call.respondWrite(ContentType.parse(TextFormat.CONTENT_TYPE_004)) {
                    TextFormat.write004(this, collectorRegistry.filteredMetricFamilySamples(names))
                }
            }
            if (!disablePdfGet) {
                get("/api/v1/genpdf/{applicationName}/{template}") {
                    val template = call.parameters["template"]!!
                    val applicationName = call.parameters["applicationName"]!!
                    val dataFile = Paths.get("data", applicationName, "$template.json")
                    val data = objectMapper.readValue(if (Files.exists(dataFile)) {
                        Files.readAllBytes(dataFile)
                    } else {
                        "{}".toByteArray(Charsets.UTF_8)
                    }, JsonNode::class.java)
                    render(applicationName, template, loadTemplates(), data)?.let {
                        call.respond(PdfContent(it, template))
                    } ?: call.respondText("Template or application not found", status = HttpStatusCode.NotFound)
                }
            }
            post("/api/v1/genpdf/{applicationName}/{template}") {
                val startTime = System.currentTimeMillis()
                val template = call.parameters["template"]!!
                val applicationName = call.parameters["applicationName"]!!
                val jsonNode = objectMapper.readValue(call.receiveStream(), JsonNode::class.java)
                log.info("JSON: {}", jsonNode.toString())
                render(applicationName, template, templates, jsonNode)?.let {
                    call.respond(PdfContent(it, template))
                    log.info("Done generating PDF in ${System.currentTimeMillis() - startTime}ms")
                } ?: call.respondText("Template or application not found", status = HttpStatusCode.NotFound)
            }
        }
    }
}

fun render(applicationName: String, template: String, templates: Map<Pair<String, String>, Template>, jsonNode: JsonNode): Document? {
    val html = HANDLEBARS_RENDERING_SUMMARY.startTimer().use {
        templates[applicationName to template]?.apply(Context
                .newBuilder(jsonNode)
                .resolver(JsonNodeValueResolver.INSTANCE)
                .build())
    }
    return if (html != null) {
        log.debug("Generated HTML {}", keyValue("html", html))
        JSOUP_PARSE_SUMMARY.startTimer().use {
            val doc = Jsoup.parse(html)
            W3CDom().fromJsoup(doc)
        }
    } else {
        null
    }
}

fun loadTemplates() = Files.list(templateRoot)
        .filter {
            !Files.isHidden(it) && Files.isDirectory(it)
        }
        .map {
            it.fileName.toString() to Files.list(it).filter { it.fileName.extension == "hbs" }
        }
        .flatMap { (applicationName, templateFiles) ->
            templateFiles.map {
                val fileName = it.fileName.toString()
                val templateName = fileName.substring(0..fileName.length - 5)
                val templateBytes = Files.readAllBytes(it).toString(Charsets.UTF_8)
                val xhtml = handlebars.compile(StringTemplateSource(fileName, templateBytes))
                (applicationName to templateName) to xhtml
            }
        }
        .toList()
        .toMap()

fun loadImages() = Files.list(imagesRoot)
        .filter {
            val validExtensions = setOf("jpg", "jpeg", "png", "bmp")
            !Files.isHidden(it) && it.fileName.extension in validExtensions
        }
        .map {
            val fileName = it.fileName.toString()
            val extension = if (it.fileName.extension == "jpg") "jpeg" else it.fileName.extension // jpg is not a valid mime-type
            val base64string = base64encoder.encodeToString(Files.readAllBytes(it))
            val base64 = "data:image/$extension;base64,$base64string"
            fileName to base64
        }
        .toList()
        .toMap()
