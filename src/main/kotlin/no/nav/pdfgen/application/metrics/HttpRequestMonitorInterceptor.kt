package no.nav.pdfgen.application.metrics

import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.path
import io.ktor.util.pipeline.PipelineContext

fun monitorHttpRequests(): suspend PipelineContext<Unit, ApplicationCall>.(Unit) -> Unit {
    return {
        val label = context.request.path()
        val timer = HTTP_HISTOGRAM.labels(label).startTimer()
        proceed()
        timer.observeDuration()
    }
}
