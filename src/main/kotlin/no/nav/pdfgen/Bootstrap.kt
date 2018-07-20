package no.nav.pdfgen

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.jknack.handlebars.Context
import com.github.jknack.handlebars.Handlebars
import com.github.jknack.handlebars.Helper
import com.github.jknack.handlebars.JsonNodeValueResolver
import com.github.jknack.handlebars.io.FileTemplateLoader
import com.github.jknack.handlebars.io.StringTemplateSource
import io.ktor.application.call
import io.ktor.application.log
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
import java.nio.file.Files
import java.nio.file.Paths
import java.time.format.DateTimeFormatter
import kotlin.streams.toList

val collectorRegistry: CollectorRegistry = CollectorRegistry.defaultRegistry
val objectMapper: ObjectMapper = ObjectMapper()
val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

fun main(args: Array<String>) {
    val templateRoot = Paths.get("templates/")
    val handlebars = Handlebars(FileTemplateLoader(templateRoot.toFile())).apply {
        registerHelper("iso_to_nor_date", Helper<String> {
            context, _ ->
            if (context == null) "" else dateFormat.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(context))
        })
    }

    val templates = Files.list(templateRoot)
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
            post("/api/v1/genpdf/{applicationName}/{template}") {
                val startTime = System.currentTimeMillis()
                val jsonNode = objectMapper.readValue(call.receiveStream(), JsonNode::class.java)
                println(objectMapper.writeValueAsString(jsonNode))
                val html = templates[call.parameters["applicationName"] to call.parameters["template"]]?.apply(Context
                        .newBuilder(jsonNode)
                        .resolver(JsonNodeValueResolver.INSTANCE)
                        .build())
                if (html != null) {
                    log.debug("Generated HTML {}", keyValue("html", html))
                    call.respondBytes(ContentType.parse("application/pdf")) {
                        val doc = Jsoup.parse(html)
                        val w3doc = W3CDom().fromJsoup(doc)

                        createPDFA(w3doc, call.parameters["template"]!!).toByteArray()
                    }
                    log.info("Done generating PDF in ${System.currentTimeMillis() - startTime}ms")
                } else {
                    call.respondText("Template or application not found", status = HttpStatusCode.NotFound)
                }
            }
        }
    }.start(wait = true)
}
