package no.nav.pdfgen.api

import com.fasterxml.jackson.databind.JsonNode
import com.github.jknack.handlebars.Context
import com.github.jknack.handlebars.JsonNodeValueResolver
import com.github.jknack.handlebars.Template
import com.github.jknack.handlebars.context.MapValueResolver
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.contentType
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Paths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.logstash.logback.argument.StructuredArguments
import no.nav.pdfgen.*
import no.nav.pdfgen.pdf.PdfContent
import no.nav.pdfgen.pdf.createPDFA
import no.nav.pdfgen.template.TemplateMap
import no.nav.pdfgen.template.loadTemplates

fun Routing.setupGeneratePdfApi(env: Environment, templates: TemplateMap) {
    route("/api/v1/genpdf") {
        if (!env.disablePdfGet) {
            get("/{applicationName}/{template}") {
                val hotTemplates = loadTemplates(env)
                createHtml(call, hotTemplates, true)?.let { document ->
                    call.respond(PdfContent(document, env))
                }
                    ?: call.respondText(
                        "Template or application not found",
                        status = HttpStatusCode.NotFound
                    )
            }
        }
        post("/{applicationName}/{template}") {
            val startTime = System.currentTimeMillis()
            createHtml(call, templates)?.let { document ->
                call.respond(PdfContent(document, env))
                log.info("Done generating PDF in ${System.currentTimeMillis() - startTime}ms")
            }
                ?: call.respondText(
                    "Template or application not found",
                    status = HttpStatusCode.NotFound
                )
        }
        post("/html/{applicationName}") {
            val applicationName = call.parameters["applicationName"]!!
            val timer =
                OPENHTMLTOPDF_RENDERING_SUMMARY.labels(applicationName, "converthtml").startTimer()

            val html = call.receiveText()

            call.respond(PdfContent(html, env))
            log.info(
                "Generated PDF using HTML template for $applicationName om ${timer.observeDuration()}ms"
            )
        }
        post("/image/{applicationName}") {
            val applicationName = call.parameters["applicationName"]!!
            val timer =
                OPENHTMLTOPDF_RENDERING_SUMMARY.labels(applicationName, "convertjpeg").startTimer()

            when (call.request.contentType()) {
                ContentType.Image.JPEG,
                ContentType.Image.PNG -> {
                    withContext(Dispatchers.IO) {
                        call.receive<InputStream>().use { inputStream ->
                            ByteArrayOutputStream().use { outputStream ->
                                createPDFA(inputStream, outputStream, env)
                                call.respondBytes(
                                    outputStream.toByteArray(),
                                    contentType = ContentType.Application.Pdf
                                )
                            }
                        }
                    }
                }
                else -> call.respond(HttpStatusCode.UnsupportedMediaType)
            }
            log.info(
                "Generated PDF using image for $applicationName om ${timer.observeDuration()}ms"
            )
        }
    }
    if (env.enableHtmlEndpoint) {
        route("/api/v1/genhtml") {
            if (!env.disablePdfGet) {
                get("/{applicationName}/{template}") {
                    val hotTemplates = loadTemplates(env)
                    createHtml(call, hotTemplates, true)?.let { call.respond(it) }
                        ?: call.respondText(
                            "Template or application not found",
                            status = HttpStatusCode.NotFound
                        )
                }
            }

            post("/{applicationName}/{template}") {
                val startTime = System.currentTimeMillis()
                createHtml(call, templates)?.let {
                    call.respond(it)
                    log.info("Done generating HTML in ${System.currentTimeMillis() - startTime}ms")
                }
                    ?: call.respondText(
                        "Template or application not found",
                        status = HttpStatusCode.NotFound
                    )
            }
        }
    }
}

private fun hotTemplateData(applicationName: String, template: String): JsonNode {
    val dataFile = Paths.get("data", applicationName, "$template.json")
    val data =
        objectMapper.readValue(
            if (Files.exists(dataFile)) {
                Files.readAllBytes(dataFile)
            } else {
                "{}".toByteArray(Charsets.UTF_8)
            },
            JsonNode::class.java,
        )
    return data
}

private suspend fun createHtml(
    call: ApplicationCall,
    templates: TemplateMap,
    useHottemplate: Boolean = false,
): String? {
    val template = call.parameters["template"]!!
    val applicationName = call.parameters["applicationName"]!!
    val jsonNode =
        if (useHottemplate) hotTemplateData(applicationName, template) else call.receive()
    log.debug("JSON: {}", objectMapper.writeValueAsString(jsonNode))
    return render(applicationName, template, templates, jsonNode)
}

fun render(
    applicationName: String,
    template: String,
    templates: Map<Pair<String, String>, Template>,
    jsonNode: JsonNode
): String? {
    return HANDLEBARS_RENDERING_SUMMARY.startTimer()
        .use {
            templates[applicationName to template]?.apply(
                Context.newBuilder(jsonNode)
                    .resolver(
                        JsonNodeValueResolver.INSTANCE,
                        MapValueResolver.INSTANCE,
                    )
                    .build(),
            )
        }
        ?.let { html ->
            log.debug("Generated HTML {}", StructuredArguments.keyValue("html", html))

            /* Uncomment to output html to file for easier debug
             *        File("pdf.html").bufferedWriter().use { out ->
             *            out.write(html)
             *        }
             */
            html
        }
}
