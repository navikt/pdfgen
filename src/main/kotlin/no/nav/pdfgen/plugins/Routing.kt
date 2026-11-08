package no.nav.pdfgen.plugins

import io.ktor.server.application.Application
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.micrometer.core.instrument.MeterRegistry
import no.nav.pdfgen.Environment
import no.nav.pdfgen.application.api.ApiPaths
import no.nav.pdfgen.application.api.pdf.registerGenerateHtmlApi
import no.nav.pdfgen.application.api.pdf.registerGeneratePdfApi
import no.nav.pdfgen.application.metrics.createPrometheusMeterRegistry

fun Application.configureRouting(
    environment: Environment,
    meterRegistry: MeterRegistry = createPrometheusMeterRegistry(),
) {
    routing {
        route(ApiPaths.GENERATE_PDF) {
            registerGeneratePdfApi(
                env = environment,
                meterRegistry = meterRegistry,
            )
        }

        route(ApiPaths.GENERATE_HTML) {
            registerGenerateHtmlApi(env = environment)
        }
    }
}
