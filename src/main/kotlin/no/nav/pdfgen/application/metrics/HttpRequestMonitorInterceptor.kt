package no.nav.pdfgen.application.metrics

import io.ktor.server.application.PipelineCall
import io.ktor.server.request.path
import io.ktor.util.pipeline.PipelineInterceptor

fun monitorHttpRequests(): PipelineInterceptor<Unit, PipelineCall> = {
    val label = context.request.path()
    val timer = HTTP_HISTOGRAM.labels(label).startTimer()
    proceed()
    timer.observeDuration()
}
