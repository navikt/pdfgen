package no.nav.pdfgen.plugins

import io.ktor.server.application.*
import io.ktor.server.routing.*
import no.nav.pdfgen.ApplicationState
import no.nav.pdfgen.application.api.nais.registerNaisApi
import no.nav.pdfgen.application.metrics.monitorHttpRequests

fun Application.configureNaisThings(applicationState: ApplicationState) {
    routing { registerNaisApi(applicationState) }
    intercept(ApplicationCallPipeline.Monitoring, monitorHttpRequests())
}
