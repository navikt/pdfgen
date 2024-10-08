package no.nav.pdfgen.application.api.pdf

import com.fasterxml.jackson.databind.JsonNode
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.pdfgen.Environment
import no.nav.pdfgen.core.pdf.createHtml
import no.nav.pdfgen.core.pdf.createHtmlFromTemplateData
import no.nav.pdfgen.logger

fun Route.registerGenerateHtmlApi(env: Environment = Environment()) {
    if (env.enableHtmlEndpoint) {
        route("/api/v1/genhtml") {
            if (!env.disablePdfGet) {
                get("/{applicationName}/{template}") {
                    val template = call.parameters["template"]!!
                    val applicationName = call.parameters["applicationName"]!!
                    createHtmlFromTemplateData(template, applicationName)?.let { call.respond(it) }
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

                createHtml(template, applicationName, jsonNode)?.let {
                    call.respond(it)
                    logger.info(
                        "Done generating HTML in ${System.currentTimeMillis() - startTime}ms"
                    )
                }
                    ?: call.respondText(
                        "Template or application not found",
                        status = HttpStatusCode.NotFound
                    )
            }
        }
    }
}
