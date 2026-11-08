package no.nav.pdfgen.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopPreparing
import io.ktor.server.application.log
import no.nav.pdfgen.application.api.isReadyKey

fun Application.configureLifecycleHooks() {
    monitor.subscribe(ApplicationStopPreparing) {
        attributes.put(isReadyKey, false)
        log.info("Shutting down application (ApplicationStopPreparing)")
    }
}
