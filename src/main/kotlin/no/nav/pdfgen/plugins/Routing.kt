package no.nav.pdfgen.plugins

import io.ktor.server.application.*
import io.ktor.server.routing.*
import no.nav.pdfgen.Environment
import no.nav.pdfgen.application.api.pdf.registerGenerateHtmlApi
import no.nav.pdfgen.application.api.pdf.registerGeneratePdfApi
import no.nav.pdfgen.application.metrics.monitorHttpRequests

fun Application.configureRouting(environment: Environment) {
    routing {
        route("/api/v1/genpdf") { registerGeneratePdfApi(environment) }
        route("/api/v1/genhtml") { registerGenerateHtmlApi(environment) }
    }
    intercept(ApplicationCallPipeline.Monitoring, monitorHttpRequests())

}
