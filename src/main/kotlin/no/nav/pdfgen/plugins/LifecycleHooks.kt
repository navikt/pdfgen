package no.nav.pdfgen.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopPreparing
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.ServerReady
import no.nav.pdfgen.ApplicationState

fun Application.configureLifecycleHooks(applicationState: ApplicationState) {
    monitor.subscribe(ApplicationStarted) { applicationState.alive = true }
    monitor.subscribe(ServerReady) { applicationState.ready = true }
    monitor.subscribe(ApplicationStopPreparing) { applicationState.ready = false }
    monitor.subscribe(ApplicationStopped) { applicationState.alive = false }
}
