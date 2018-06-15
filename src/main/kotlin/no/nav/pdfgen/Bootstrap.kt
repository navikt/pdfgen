package no.nav.pdfgen

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.jknack.handlebars.Context
import com.github.jknack.handlebars.Handlebars
import com.github.jknack.handlebars.Helper
import com.github.jknack.handlebars.JsonNodeValueResolver
import com.github.jknack.handlebars.io.FileTemplateLoader
import com.github.jknack.handlebars.io.StringTemplateSource
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import io.ktor.server.netty.*
import io.ktor.routing.*
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.receiveStream
import io.ktor.response.*
import io.ktor.server.engine.*
import io.ktor.util.extension
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.exporter.common.TextFormat
import org.jsoup.Jsoup
import org.jsoup.helper.W3CDom
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import kotlin.streams.toList

val collectorRegistry: CollectorRegistry = CollectorRegistry.defaultRegistry
val objectMapper: ObjectMapper = ObjectMapper()

fun main(args: Array<String>) {
    val templateRoot = Paths.get("templates/")
    val handlebars = Handlebars(FileTemplateLoader(templateRoot.toFile()))
    handlebars.registerHelper("iso_to_nor_date", Helper<String> {
        context, options ->
        options.hash("Test date")
    })

    val templates = Files.list(templateRoot)
            .map {
                it.fileName.toString() to Files.list(it)
                        .filter { it.fileName.extension == "hbs" }
            }
            .flatMap {
                (applicationName, templateFiles) ->
                templateFiles
                        .map {
                            val fileName = it.fileName.toString()
                            val templateName = fileName.substring(0..fileName.length - 5)
                            val xhtml = handlebars.compile(StringTemplateSource(fileName, Files.readAllBytes(it).toString(Charsets.UTF_8)))
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
                    val fileName = "${UUID.randomUUID()}.pdf"
                    val doc = Jsoup.parse(html)
                    val w3doc = W3CDom().fromJsoup(doc)
                    PdfRendererBuilder()
                            .withW3cDocument(w3doc, "")
                            .toStream(FileOutputStream(File("out", fileName)))
                            .run()
                    call.respondFile(File("out", fileName))
                    log.info("Done generating PDF in ${System.currentTimeMillis() - startTime}ms")
                } else {
                    call.respondText("Template or application not found", status = HttpStatusCode.NotFound)
                }
            }
        }
    }.start(wait = true)
}
