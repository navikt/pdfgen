package no.nav.pdfgen.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
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
