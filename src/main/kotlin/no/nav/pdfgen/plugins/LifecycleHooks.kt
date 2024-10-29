package no.nav.pdfgen.plugins

import io.ktor.server.application.*
import no.nav.pdfgen.ApplicationState

fun Application.configureLifecycleHooks(applicationState: ApplicationState) {

    monitor.subscribe(ApplicationStarted) { applicationState.ready = true }
    monitor.subscribe(ApplicationStopped) {
        applicationState.ready = false
        applicationState.alive = false
    }
}
