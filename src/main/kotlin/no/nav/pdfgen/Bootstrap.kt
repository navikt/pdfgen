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
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.request.receiveStream
import io.ktor.response.respondBytes
import io.ktor.response.respondText
import io.ktor.response.respondWrite
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.extension
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.exporter.common.TextFormat
import net.logstash.logback.argument.StructuredArguments.keyValue
import org.jsoup.Jsoup
import org.jsoup.helper.W3CDom
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Files
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
    registerHelper("iso_to_nor_date", Helper<String> {
        context, _ ->
        if (context == null) "" else dateFormat.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(context))
    })
    registerHelper("split_person_id", Helper<Long> {
        context, _ ->
        if (context == null || context.toString().length < 11) "" else (context.toString().substring(0, 6) + " " + context.toString().substring(6, 11))
    })
    registerHelper("eq", Helper<String> {
        context, options ->
        if (context == options.param(0)) {
            options.fn()
        } else {
            options.inverse()
        }
    })
    registerHelper("safe", Helper<String> {
        context, _ ->
        if (context == null) "" else Handlebars.SafeString(context)
    })

    registerHelper("image", Helper<String> {
        context, _ ->
        if (context == null) {
            ""
        } else {
            images[context]
        }
    })
}
val log: Logger = LoggerFactory.getLogger("pdf-gen")

fun main(args: Array<String>) {
    System.setProperty("sun.java2d.cmm", "sun.java2d.cmm.kcms.KcmsServiceProvider")
    val templates = loadTemplates()

    embeddedServer(Netty, 8080) {
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
            get("/api/v1/genpdf/{applicationName}/{template}") {
                val template = call.parameters["template"]
                val applicationName = call.parameters["applicationName"]
                val dataFile = Paths.get("data", applicationName, "$template.json")
                val data = if (Files.exists(dataFile)) {
                    Files.readAllBytes(dataFile)
                } else {
                    "{}".toByteArray(Charsets.UTF_8)
                }
                render(loadTemplates(), call, objectMapper.readValue(data, JsonNode::class.java))
            }
            post("/api/v1/genpdf/{applicationName}/{template}") {
                val jsonNode = objectMapper.readValue(call.receiveStream(), JsonNode::class.java)
                println(objectMapper.writeValueAsString(jsonNode))
                render(templates, call, jsonNode)
            }
        }
    }.start(wait = true)
}

suspend fun render(templates: Map<Pair<String, String>, Template>, call: ApplicationCall, jsonNode: JsonNode) {
    val startTime = System.currentTimeMillis()
    val template = call.parameters["template"]
    val html = templates[call.parameters["applicationName"] to template]?.apply(Context
            .newBuilder(jsonNode)
            .resolver(JsonNodeValueResolver.INSTANCE)
            .build())
    if (template != null && html != null) {
        log.debug("Generated HTML {}", keyValue("html", html))
        call.respondBytes(ContentType.parse("application/pdf")) {
            val doc = Jsoup.parse(html)
            val w3doc = W3CDom().fromJsoup(doc)

            createPDFA(w3doc, template)
        }
        log.info("Done generating PDF in ${System.currentTimeMillis() - startTime}ms")
    } else {
        call.respondText("Template or application not found", status = HttpStatusCode.NotFound)
    }
}

fun loadTemplates() = Files.list(templateRoot)
        .filter {
            !Files.isHidden(it)
        }
        .map {
            it.fileName.toString() to Files.list(it).filter { it.fileName.extension == "hbs" }
        }
        .flatMap {
            (applicationName, templateFiles) ->
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
            !Files.isHidden(it)
        }
        .map {
            val fileName = it.fileName.toString()
            val extension = it.fileName.extension
            val base64string = base64encoder.encodeToString(Files.readAllBytes(it))
            val base64 = "data:image/$extension;base64,$base64string"
            fileName to base64
        }
        .toList()
        .toMap()
