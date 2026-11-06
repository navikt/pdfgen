package no.nav.pdfgen.application.api.nais

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.pdfgen.ApplicationState

private val prometheusContentType = ContentType.parse("text/plain; version=0.0.4; charset=utf-8")

fun Routing.registerNaisApi(
    applicationState: ApplicationState,
    readynessCheck: () -> Boolean = { applicationState.ready },
    alivenessCheck: () -> Boolean = { applicationState.alive },
    meterRegistry: PrometheusMeterRegistry,
) {
    get("/internal/is_alive") {
        if (alivenessCheck()) {
            call.respondText("I'm alive")
        } else {
            call.respondText("I'm dead x_x", status = HttpStatusCode.InternalServerError)
        }
    }

    get("/internal/is_ready") {
        if (readynessCheck()) {
            call.respondText("I'm ready")
        } else {
            call.respondText(
                "Please wait! I'm not ready :(",
                status = HttpStatusCode.InternalServerError,
            )
        }
    }

    get("/internal/prometheus") {
        val names = call.request.queryParameters
            .getAll("name[]")
            ?.toSet()
            .orEmpty()

        val metrics = filterMetricFamilies(
            scrape = meterRegistry.scrape(),
            names = names,
        )

        call.respondText(
            text = metrics,
            contentType = prometheusContentType,
        )
    }
}

/**
 * Filters a Prometheus text-format scrape to the requested metric families.
 *
 * An empty [names] set returns the scrape unchanged. Otherwise, each `# HELP` line starts a
 * family, and the filter keeps that line and its following lines until the next family.
 */
private fun filterMetricFamilies(
    scrape: String,
    names: Set<String>,
): String {
    if (names.isEmpty()) return scrape

    var includeFamily = false
    val lines = scrape
        .lineSequence()
        .filter { line ->
            if (line.startsWith("# HELP ")) {
                // A HELP line starts a family and sets whether its lines are included.
                includeFamily = line.removePrefix("# HELP ").substringBefore(' ') in names
            }
            includeFamily
        }.toList()

    return if (lines.isEmpty()) "" else lines.joinToString("\n", postfix = "\n")
}
