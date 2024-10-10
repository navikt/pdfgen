package no.nav.pdfgen.plugins

import io.ktor.server.application.*
import no.nav.pdfgen.ApplicationState

fun Application.configureLifecycleHooks(applicationState: ApplicationState) {

    this.monitor.subscribe(ApplicationStarted) { applicationState.ready = true }
    this.monitor.subscribe(ApplicationStopped) { applicationState.ready = false }
}
