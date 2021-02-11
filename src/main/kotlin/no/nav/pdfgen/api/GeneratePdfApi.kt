package no.nav.pdfgen.api

import com.fasterxml.jackson.databind.JsonNode
import com.github.jknack.handlebars.Context
import com.github.jknack.handlebars.JsonNodeValueResolver
import com.github.jknack.handlebars.Template
import com.github.jknack.handlebars.context.MapValueResolver
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.logstash.logback.argument.StructuredArguments
import no.nav.pdfgen.*
import no.nav.pdfgen.pdf.PdfContent
import no.nav.pdfgen.pdf.createPDFA
import no.nav.pdfgen.pdf.verifyCompliance
import no.nav.pdfgen.template.TemplateMap
import org.jsoup.Jsoup
import org.jsoup.helper.W3CDom
import org.w3c.dom.Document
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Paths

fun Routing.setupGeneratePdfApi(env: Environment, templates: TemplateMap) {
    route("/api/v1/genpdf") {
        if (!env.disablePdfGet) {
            get("/{applicationName}/{template}") {
                val template = call.parameters["template"]!!
                val applicationName = call.parameters["applicationName"]!!
                val dataFile = Paths.get("data", applicationName, "$template.json")
                val data = objectMapper.readValue(
                    if (Files.exists(dataFile)) {
                        Files.readAllBytes(dataFile)
                    } else {
                        "{}".toByteArray(Charsets.UTF_8)
                    },
                    JsonNode::class.java
                )
                render(applicationName, template, templates, data)?.let { document ->
                    val pdf = ByteArrayOutputStream().apply {
                        createPDFA(document, this, env)
                    }.toByteArray()
                    val result = verifyCompliance(pdf)
                    log.info("Valid PDF/A-2U: $result")
                    call.respondBytes(pdf, contentType = ContentType.Application.Pdf)
                } ?: call.respondText("Template or application not found", status = HttpStatusCode.NotFound)
            }
        }
        post("/{applicationName}/{template}") {
            val startTime = System.currentTimeMillis()
            val template = call.parameters["template"]!!
            val applicationName = call.parameters["applicationName"]!!
            val jsonNode = call.receive<JsonNode>()
            log.debug("JSON: {}", objectMapper.writeValueAsString(jsonNode))
            render(applicationName, template, templates, jsonNode)?.let { document ->
                call.respond(PdfContent(document, env))
                log.info("Done generating PDF in ${System.currentTimeMillis() - startTime}ms")
            } ?: call.respondText("Template or application not found", status = HttpStatusCode.NotFound)
        }
        post("/html/{applicationName}") {
            val applicationName = call.parameters["applicationName"]!!
            val timer = OPENHTMLTOPDF_RENDERING_SUMMARY.labels(applicationName, "converthtml").startTimer()

            val html = call.receiveText()

            ByteArrayOutputStream().use { bytes ->
                createPDFA(fromHtmlToDocument(html), bytes, env)
                call.respondBytes(bytes.toByteArray(), contentType = ContentType.Application.Pdf)
            }
            log.info("Generated PDF using HTML template for $applicationName om ${timer.observeDuration()}ms")
        }
        post("/image/{applicationName}") {
            val applicationName = call.parameters["applicationName"]!!
            val timer = OPENHTMLTOPDF_RENDERING_SUMMARY.labels(applicationName, "convertjpeg").startTimer()

            when (call.request.contentType()) {
                ContentType.Image.JPEG, ContentType.Image.PNG -> {
                    withContext(Dispatchers.IO) {
                        call.receive<InputStream>().use { inputStream ->
                            ByteArrayOutputStream().use { outputStream ->
                                createPDFA(inputStream, outputStream, env)
                                call.respondBytes(outputStream.toByteArray(), contentType = ContentType.Application.Pdf)
                            }
                        }
                    }
                }
                else -> call.respond(HttpStatusCode.UnsupportedMediaType)
            }
            log.info("Generated PDF using image for $applicationName om ${timer.observeDuration()}ms")
        }
    }
}

fun fromHtmlToDocument(html: String): Document = JSOUP_PARSE_SUMMARY.startTimer().use {
    val doc = Jsoup.parse(html)
    W3CDom().fromJsoup(doc)
}

fun render(applicationName: String, template: String, templates: Map<Pair<String, String>, Template>, jsonNode: JsonNode): Document? {
    return HANDLEBARS_RENDERING_SUMMARY.startTimer().use {
        templates[applicationName to template]?.apply(
            Context
                .newBuilder(jsonNode)
                .resolver(
                    JsonNodeValueResolver.INSTANCE,
                    MapValueResolver.INSTANCE
                )
                .build()
        )
    }?.let { html ->
        log.debug("Generated HTML {}", StructuredArguments.keyValue("html", html))

/* Uncomment to output html to file for easier debug
*        File("pdf.html").bufferedWriter().use { out ->
*            out.write(html)
*        }
*/
        fromHtmlToDocument(html)
    }
}
