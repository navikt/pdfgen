package no.nav.pdfgen.plugins

import io.ktor.server.application.Application
import io.ktor.server.routing.routing
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.pdfgen.application.api.registerHealthApi

fun Application.configureHealthAndMetrics(meterRegistry: PrometheusMeterRegistry) {
    routing { registerHealthApi(meterRegistry = meterRegistry) }
}
