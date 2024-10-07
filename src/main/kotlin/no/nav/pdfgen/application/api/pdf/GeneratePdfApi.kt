package no.nav.pdfgen.application.api.pdf

import com.fasterxml.jackson.databind.JsonNode
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.contentType
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.*
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.pdfgen.Environment
import no.nav.pdfgen.core.OPENHTMLTOPDF_RENDERING_SUMMARY
import no.nav.pdfgen.core.pdf.createHtml
import no.nav.pdfgen.core.pdf.createHtmlFromTemplateData
import no.nav.pdfgen.core.pdf.createPDFA
import no.nav.pdfgen.logger

fun Route.registerGeneratePdfApi(env: Environment = Environment()) {
    if (!env.disablePdfGet) {
        get("/{applicationName}/{template}") {
            val template = call.parameters["template"]!!
            val applicationName = call.parameters["applicationName"]!!
            createHtmlFromTemplateData(template, applicationName)?.let { document ->
                call.respond(createPDFA(document))
            }
                ?: call.respondText(
                    "Template or application not found",
                    status = HttpStatusCode.NotFound
                )
        }
    }
    post("/{applicationName}/{template}") {
        val startTime = System.currentTimeMillis()
        val template = call.parameters["template"]!!
        val applicationName = call.parameters["applicationName"]!!
        val jsonNode: JsonNode = call.receive()

        createHtml(template, applicationName, jsonNode)?.let { document ->
            call.respond(createPDFA(document))
            logger.info("Done generating PDF in ${System.currentTimeMillis() - startTime}ms")
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

        call.respond(createPDFA(html))
        logger.info(
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
                            createPDFA(inputStream, outputStream)
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
        logger.info(
            "Generated PDF using image for $applicationName om ${timer.observeDuration()}ms"
        )
    }
}
