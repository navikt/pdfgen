package no.nav.pdfgen.plugins

import io.ktor.server.application.*
import no.nav.pdfgen.ApplicationState

fun Application.configureLifecycleHooks(applicationState: ApplicationState) {
    monitor.subscribe(ApplicationStarted) { applicationState.alive = true }
    monitor.subscribe(ServerReady) { applicationState.ready = true }
    monitor.subscribe(ApplicationStopPreparing) { applicationState.ready = false }
    monitor.subscribe(ApplicationStopped) { applicationState.alive = false }
}
