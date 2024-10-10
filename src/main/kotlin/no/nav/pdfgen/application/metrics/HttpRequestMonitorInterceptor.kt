package no.nav.pdfgen.application.metrics

import io.ktor.server.application.*
import io.ktor.server.request.path
import io.ktor.util.pipeline.*

fun monitorHttpRequests(): PipelineInterceptor<Unit, PipelineCall> {
    return {
        val label = context.request.path()
        val timer = HTTP_HISTOGRAM.labels(label).startTimer()
        proceed()
        timer.observeDuration()
    }
}
