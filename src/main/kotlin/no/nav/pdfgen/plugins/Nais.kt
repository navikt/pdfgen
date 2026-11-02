package no.nav.pdfgen.plugins

import io.ktor.server.application.Application
import io.ktor.server.routing.routing
import no.nav.pdfgen.ApplicationState
import no.nav.pdfgen.application.api.nais.registerNaisApi

fun Application.configureNais(applicationState: ApplicationState) {
    routing { registerNaisApi(applicationState) }
}
