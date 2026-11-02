package no.nav.pdfgen.application.metrics

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.routing.RoutingRoot
import io.ktor.util.AttributeKey

private val routeLabelKey = AttributeKey<String>("HttpRequestMetricRoute")
private val monitorInstalledKey = AttributeKey<Boolean>("HttpRequestMonitorInstalled")
private const val UNMATCHED_ROUTE = "unmatched"

fun Application.monitorHttpRequests() {
    if (attributes.getOrNull(monitorInstalledKey) == true) return

    monitor.subscribe(RoutingRoot.RoutingCallStarted) { routingCall ->
        routingCall.pipelineCall.engineCall.attributes
            .put(routeLabelKey, routingCall.route.toString())
    }

    intercept(ApplicationCallPipeline.Monitoring) {
        val startNanos = System.nanoTime()
        try {
            proceed()
        } finally {
            val routeLabel = context.attributes.getOrNull(routeLabelKey) ?: UNMATCHED_ROUTE
            val elapsedSeconds = (System.nanoTime() - startNanos) / 1_000_000_000.0
            HTTP_HISTOGRAM.labels(routeLabel).observe(elapsedSeconds)
        }
    }

    attributes.put(monitorInstalledKey, true)
}
