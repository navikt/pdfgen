package no.nav.pdfgen.application.api.pdf

import com.fasterxml.jackson.databind.JsonNode
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.request.requirePathParameter
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import no.nav.pdfgen.Environment
import no.nav.pdfgen.application.api.APPLICATION_NAME_PARAMETER
import no.nav.pdfgen.application.api.ApiMessages
import no.nav.pdfgen.application.api.TEMPLATE_PARAMETER
import no.nav.pdfgen.core.pdf.createHtml
import no.nav.pdfgen.core.pdf.createHtmlFromTemplateData
import no.nav.pdfgen.logger

fun Route.registerGenerateHtmlApi(env: Environment = Environment()) {
    if (env.enableHtmlEndpoint) {
        if (!env.disablePdfGet) {
            get("/{applicationName}/{template}") {
                val template = call.requirePathParameter(TEMPLATE_PARAMETER)
                val applicationName = call.requirePathParameter(APPLICATION_NAME_PARAMETER)
                createHtmlFromTemplateData(template, applicationName)?.let {
                    call.response.header(HttpHeaders.ContentType, ContentType.Text.Html.toString())
                    call.respond(it)
                }
                    ?: call.respondText(
                        ApiMessages.TEMPLATE_OR_APPLICATION_NOT_FOUND,
                        status = HttpStatusCode.NotFound,
                    )
            }
        }

        post("/{applicationName}/{template}") {
            val startTime = System.currentTimeMillis()
            val template = call.requirePathParameter(TEMPLATE_PARAMETER)
            val applicationName = call.requirePathParameter(APPLICATION_NAME_PARAMETER)
            val jsonNode: JsonNode = call.receive()

            createHtml(template, applicationName, jsonNode)?.let {
                call.response.header(HttpHeaders.ContentType, ContentType.Text.Html.toString())
                call.respond(it)
                logger.info("Done generating HTML in ${System.currentTimeMillis() - startTime}ms")
            } ?: call.respondText(
                ApiMessages.TEMPLATE_OR_APPLICATION_NOT_FOUND,
                status = HttpStatusCode.NotFound,
            )
        }
    }
}
