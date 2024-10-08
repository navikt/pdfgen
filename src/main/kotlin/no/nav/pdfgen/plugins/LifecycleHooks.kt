package no.nav.pdfgen.plugins

import io.ktor.server.application.*
import no.nav.pdfgen.ApplicationState

fun Application.configureLifecycleHooks(applicationState: ApplicationState) {

    environment.monitor.subscribe(ApplicationStarted) { applicationState.ready = true }
    environment.monitor.subscribe(ApplicationStopped) { applicationState.ready = false }
}
