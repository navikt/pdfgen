package no.nav.pdfgen.application.api.nais

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.exporter.common.TextFormat
import io.prometheus.client.exporter.common.TextFormat.write004
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import no.nav.pdfgen.ApplicationState

fun Routing.registerNaisApi(
    applicationState: ApplicationState,
    readynessCheck: () -> Boolean = { applicationState.ready },
    alivenessCheck: () -> Boolean = { applicationState.alive },
    collectorRegistry: CollectorRegistry = CollectorRegistry.defaultRegistry
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
                status = HttpStatusCode.InternalServerError
            )
        }
    }
    get("/internal/prometheus") {
        val names = call.request.queryParameters.getAll("name[]")?.toSet() ?: setOf()
        call.respondTextWriter(ContentType.parse(TextFormat.CONTENT_TYPE_004)) {
            CoroutineScope(Dispatchers.IO).launch {
                runCatching {
                    write004(
                        this@respondTextWriter,
                        collectorRegistry.filteredMetricFamilySamples(names),
                    )
                }
            }
        }
    }
}
