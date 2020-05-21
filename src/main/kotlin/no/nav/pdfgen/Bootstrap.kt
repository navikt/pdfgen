package no.nav.pdfgen

// Uncommemt to enable debug to file
// import java.io.File

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.github.jknack.handlebars.Context
import com.github.jknack.handlebars.Handlebars
import com.github.jknack.handlebars.JsonNodeValueResolver
import com.github.jknack.handlebars.Template
import com.github.jknack.handlebars.context.MapValueResolver
import com.github.jknack.handlebars.io.FileTemplateLoader
import com.github.jknack.handlebars.io.StringTemplateSource
import io.ktor.application.call
import io.ktor.application.feature
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.features.StatusPages
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.withCharset
import io.ktor.jackson.jackson
import io.ktor.request.contentType
import io.ktor.request.path
import io.ktor.request.receive
import io.ktor.request.receiveStream
import io.ktor.request.receiveText
import io.ktor.response.respond
import io.ktor.response.respondBytes
import io.ktor.response.respondText
import io.ktor.response.respondTextWriter
import io.ktor.routing.HttpMethodRouteSelector
import io.ktor.routing.Route
import io.ktor.routing.Routing
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.extension
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.exporter.common.TextFormat
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.Base64.Encoder
import kotlin.streams.toList
import kotlinx.coroutines.io.ByteWriteChannel
import kotlinx.coroutines.io.jvm.javaio.toOutputStream
import net.logstash.logback.argument.StructuredArguments.keyValue
import org.jsoup.Jsoup
import org.jsoup.helper.W3CDom
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.w3c.dom.Document

val APPLICATION_PDF = ContentType.parse("application/pdf")

val collectorRegistry: CollectorRegistry = CollectorRegistry.defaultRegistry
val objectMapper: ObjectMapper = ObjectMapper()
        .registerKotlinModule()
val base64encoder: Encoder = Base64.getEncoder()
val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
val datetimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
val templateRoot: Path = Paths.get("templates/")
val imagesRoot: Path = Paths.get("resources/")
val fontsRoot: Path = Paths.get("fonts/")
val images = loadImages()
val resources = loadResources()
val handlebars: Handlebars = Handlebars(FileTemplateLoader(templateRoot.toFile())).apply {
    registerNavHelpers(this)
    infiniteLoops(true)
}

val fonts: Array<FontMetadata> = objectMapper.readValue(Files.newInputStream(fontsRoot.resolve("config.json")))

val log: Logger = LoggerFactory.getLogger("pdf-gen")

fun main(args: Array<String>) {
    initializeApplication(8080).start(wait = true)
}

class PdfContent(
    private val w3Doc: Document,
    override val contentType: ContentType = APPLICATION_PDF
) : OutgoingContent.WriteChannelContent() {
    override suspend fun writeTo(channel: ByteWriteChannel) {
        channel.toOutputStream().use {
            createPDFA(w3Doc, it)
        }
    }
}

fun initializeApplication(port: Int): ApplicationEngine {
    System.setProperty("sun.java2d.cmm", "sun.java2d.cmm.kcms.KcmsServiceProvider")
    val templates = loadTemplates()
    val disablePdfGet = System.getenv("DISABLE_PDF_GET")?.let { it == "true" } ?: false

    return embeddedServer(Netty, port, configure = {
        // Increase timeout of Netty to handle large content bodies
        responseWriteTimeoutSeconds = 60
    }) {
        install(ContentNegotiation) {
            jackson {}
        }

        install(StatusPages) {
            status(HttpStatusCode.NotFound) {
                call.respond(TextContent(
                        messageFor404(templates, feature(Routing), call.request.path()),
                        ContentType.Text.Plain.withCharset(Charsets.UTF_8),
                        it
                ))
            }
        }
        routing {
            get("/is_ready") {
                call.respondText("I'm ready")
            }
            get("/is_alive") {
                call.respondText("I'm alive")
            }
            get("/prometheus") {
                val names = call.request.queryParameters.getAll("name[]")?.toSet() ?: setOf()
                call.respondTextWriter(ContentType.parse(TextFormat.CONTENT_TYPE_004)) {
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
                    render(applicationName, template, loadTemplates(), data)?.let { document ->
                        call.respond(PdfContent(document))
                    } ?: call.respondText("Template or application not found", status = HttpStatusCode.NotFound)
                }
            }
            post("/api/v1/genpdf/{applicationName}/{template}") {
                val startTime = System.currentTimeMillis()
                val template = call.parameters["template"]!!
                val applicationName = call.parameters["applicationName"]!!
                val jsonNode = call.receive<JsonNode>()
                log.debug("JSON: {}", objectMapper.writeValueAsString(jsonNode))
                render(applicationName, template, templates, jsonNode)?.let { document ->
                    call.respond(PdfContent(document))
                    log.info("Done generating PDF in ${System.currentTimeMillis() - startTime}ms")
                } ?: call.respondText("Template or application not found", status = HttpStatusCode.NotFound)
            }
            post("/api/v1/genpdf/html/{applicationName}") {
                val applicationName = call.parameters["applicationName"]!!
                val timer = OPENHTMLTOPDF_RENDERING_SUMMARY.labels(applicationName, "converthtml").startTimer()

                val html = call.receiveText()

                ByteArrayOutputStream().use { bytes ->
                    createPDFA(fromHtmlToDocument(html), bytes)
                    call.respondBytes(bytes.toByteArray(), contentType = APPLICATION_PDF)
                }

                log.info("Generated PDF using HTML template for $applicationName om ${timer.observeDuration()}ms")
            }
            post("/api/v1/genpdf/image/{applicationName}") {
                val applicationName = call.parameters["applicationName"]!!
                val timer = OPENHTMLTOPDF_RENDERING_SUMMARY.labels(applicationName, "convertjpeg").startTimer()

                when (call.request.contentType()) {
                    ContentType.Image.JPEG, ContentType.Image.PNG -> {
                        ByteArrayOutputStream().use { outputStream ->
                            createPDFA(call.receiveStream(), outputStream)
                            call.respondBytes(outputStream.toByteArray(), contentType = APPLICATION_PDF)
                        }
                    }
                    else -> call.respond(HttpStatusCode.UnsupportedMediaType)
                }
                log.info("Generated PDF using image for $applicationName om ${timer.observeDuration()}ms")
            }
        }
    }
}

private fun messageFor404(templates: Map<Pair<String, String>, Template>, routing: Routing, path: String) =
        "Unkown path '$path'. Known paths:" + templates.map { (app, _) ->
            val (applicationName, template) = app
            apiRoutes(routing)
                    .map { it.replace("{applicationName}", applicationName) }
                    .map { it.replace("{template}", template) }
                    .joinToString("\n")
        }.joinToString("\n")

private fun apiRoutes(routing: Routing) = allRoutes(routing).filter {
    it.selector is HttpMethodRouteSelector && it.toString().startsWith("/api")
}.map { it.toString() }

private fun allRoutes(route: Route): List<Route> = listOf(route) + route.children.flatMap(::allRoutes)

fun fromHtmlToDocument(html: String): Document = JSOUP_PARSE_SUMMARY.startTimer().use {
    val doc = Jsoup.parse(html)
    W3CDom().fromJsoup(doc)
}

fun render(applicationName: String, template: String, templates: Map<Pair<String, String>, Template>, jsonNode: JsonNode): Document? {
    return HANDLEBARS_RENDERING_SUMMARY.startTimer().use {
        templates[applicationName to template]?.apply(Context
                .newBuilder(jsonNode)
                .resolver(JsonNodeValueResolver.INSTANCE,
                        MapValueResolver.INSTANCE)
                .build())
    }?.let { html ->
        log.debug("Generated HTML {}", keyValue("html", html))

/* Uncomment to output html to file for easier debug
*        File("pdf.html").bufferedWriter().use { out ->
*            out.write(html)
*        }
*/
        fromHtmlToDocument(html)
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
            val base64string = base64encoder.encodeToString(Files.readAllBytes(it))
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
