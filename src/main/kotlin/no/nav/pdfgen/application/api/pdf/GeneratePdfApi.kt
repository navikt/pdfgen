package no.nav.pdfgen.application.api.pdf

import com.fasterxml.jackson.databind.JsonNode
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.contentType
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.pdfgen.Environment
import no.nav.pdfgen.application.metrics.recordPdfRendering
import no.nav.pdfgen.core.pdf.createHtml
import no.nav.pdfgen.core.pdf.createHtmlFromTemplateData
import no.nav.pdfgen.core.pdf.createPDFA
import no.nav.pdfgen.logger
import java.io.ByteArrayOutputStream
import java.io.InputStream

private fun renderTemplatePdf(
    html: String,
    meterRegistry: MeterRegistry,
): ByteArray {
    val startNanos = System.nanoTime()
    return try {
        createPDFA(html)
    } finally {
        recordPdfRendering(
            meterRegistry = meterRegistry,
            templateType = "template",
            durationNanos = System.nanoTime() - startNanos,
        )
    }
}

fun Route.registerGeneratePdfApi(
    env: Environment,
    meterRegistry: MeterRegistry,
) {
    if (!env.disablePdfGet) {
        get("/{applicationName}/{template}") {
            val template = call.parameters["template"]!!
            val applicationName = call.parameters["applicationName"]!!

            createHtmlFromTemplateData(
                template = template,
                directoryName = applicationName,
            )?.let { document ->
                call.response.header("Content-Type", ContentType.Application.Pdf.toString())
                call.respond(renderTemplatePdf(document, meterRegistry))
            } ?: call.respondText(
                "Template or application not found",
                status = HttpStatusCode.NotFound,
            )
        }
    }

    post("/{applicationName}/{template}") {
        val startTime = System.currentTimeMillis()
        val template = call.parameters["template"]!!
        val applicationName = call.parameters["applicationName"]!!
        val jsonNode: JsonNode = call.receive()

        createHtml(
            template = template,
            directoryName = applicationName,
            jsonPayload = jsonNode,
        )?.let { document ->
            call.response.header("Content-Type", ContentType.Application.Pdf.toString())
            call.respond(renderTemplatePdf(document, meterRegistry))
            logger.info("Done generating PDF in ${System.currentTimeMillis() - startTime}ms")
        } ?: call.respondText(
            "Template or application not found",
            status = HttpStatusCode.NotFound,
        )
    }

    post("/html/{applicationName}") {
        val applicationName = call.parameters["applicationName"]!!
        val startNanos = System.nanoTime()
        var durationMillis: Double
        try {
            val html = call.receiveText()

            call.response.header("Content-Type", ContentType.Application.Pdf.toString())
            call.respond(createPDFA(html))
        } finally {
            val durationNanos = System.nanoTime() - startNanos
            recordPdfRendering(
                meterRegistry = meterRegistry,
                templateType = "converthtml",
                durationNanos = durationNanos,
            )
            durationMillis = durationNanos / 1_000_000.0
        }
        logger.info("Generated PDF using HTML template for $applicationName in ${durationMillis}ms")
    }

    post("/image/{applicationName}") {
        val applicationName = call.parameters["applicationName"]!!
        val startNanos = System.nanoTime()
        var durationMillis: Double
        var generatedPdf = false
        try {
            when (call.request.contentType()) {
                ContentType.Image.JPEG,
                ContentType.Image.PNG,
                -> {
                    withContext(Dispatchers.IO) {
                        call.receive<InputStream>().use { inputStream ->
                            ByteArrayOutputStream().use { outputStream ->
                                try {
                                    createPDFA(inputStream, outputStream)
                                    call.response.header(
                                        "Content-Type",
                                        ContentType.Application.Pdf.toString(),
                                    )
                                    call.respondBytes(
                                        outputStream.toByteArray(),
                                        contentType = ContentType.Application.Pdf,
                                    )
                                    generatedPdf = true
                                } catch (e: IllegalArgumentException) {
                                    logger.error("Klarte ikke å behandle bilde for $applicationName", e)
                                    call.respondText(
                                        "Bildet kunne ikke konverteres til PDF. Kontroller at filen er et gyldig JPEG- eller PNG-bilde.",
                                        status = HttpStatusCode.UnprocessableEntity,
                                    )
                                }
                            }
                        }
                    }
                }

                else -> call.respond(HttpStatusCode.UnsupportedMediaType)
            }
        } finally {
            val durationNanos = System.nanoTime() - startNanos
            recordPdfRendering(
                meterRegistry = meterRegistry,
                templateType = "convertjpeg",
                durationNanos = durationNanos,
            )
            durationMillis = durationNanos / 1_000_000.0
        }

        if (generatedPdf) {
            logger.info("Generated PDF using image for $applicationName in ${durationMillis}ms")
        }
    }
}
