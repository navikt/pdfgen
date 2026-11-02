package no.nav.pdfgen

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.ApplicationReceivePipeline
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.prometheus.client.Collector
import io.prometheus.client.CollectorRegistry
import no.nav.pdfgen.core.OPENHTMLTOPDF_RENDERING_SUMMARY
import no.nav.pdfgen.plugins.configureRouting
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class MetricsTest {
    @Test
    internal fun `HTTP metrics should use route patterns and count failures exactly once`() {
        testApplication {
            application {
                configureRouting(Environment())
                // Gjentatt oppsett skal ikke installere en ekstra forespørselsmåler.
                configureRouting(Environment())
                install(StatusPages) {
                    exception<IllegalStateException> { call, _ ->
                        call.respond(HttpStatusCode.InternalServerError)
                    }
                }
                routing {
                    get("/metrics-cardinality/{identifier}") {
                        call.respondText("ok")
                    }
                    get("/metrics-failure/{identifier}") {
                        throw IllegalStateException("metrics test failure")
                    }
                    get("/internal/is_alive") {
                        call.respondText("I'm alive")
                    }
                }
            }

            val parameterizedPath = "/metrics-cardinality/{identifier}"
            val matchedBefore = requestCountContaining(parameterizedPath)
            val unmatchedBefore = requestCount("unmatched")
            val failedBefore = requestCountContaining("/metrics-failure/{identifier}")
            val healthBefore = requestCountContaining("/internal/is_alive")

            repeat(50) { index ->
                val response = client.get("/metrics-cardinality/value-$index")
                assertEquals(HttpStatusCode.OK, response.status)
            }
            repeat(50) { index ->
                val response = client.get("/unmatched-metrics-$index/deep/path")
                assertEquals(HttpStatusCode.NotFound, response.status)
            }
            val failedResponse = client.get("/metrics-failure/request-specific-value")
            assertEquals(HttpStatusCode.InternalServerError, failedResponse.status)
            val healthResponse = client.get("/internal/is_alive")
            assertEquals(HttpStatusCode.OK, healthResponse.status)
            assertEquals("I'm alive", healthResponse.bodyAsText())

            assertEquals(50.0, requestCountContaining(parameterizedPath) - matchedBefore)
            assertEquals(
                1,
                requestPathLabels().filter { it.contains("/metrics-cardinality") }.distinct().size,
                "Ulike parameterverdier skal dele én rutemal som etikett",
            )
            assertTrue(requestCount("unmatched") - unmatchedBefore >= 50.0)
            assertFalse(requestPathLabels().any { it.contains("/unmatched-metrics-") })
            assertEquals(
                1.0,
                requestCountContaining("/metrics-failure/{identifier}") - failedBefore,
                "Feilende håndterere skal telles nøyaktig én gang",
            )
            assertEquals(1.0, requestCountContaining("/internal/is_alive") - healthBefore)
            assertFalse(requestPathLabels().any { it.contains("request-specific-value") })
        }
    }

    @Test
    internal fun `Conversion metrics should use a fixed application label and count rejected images`() {
        testApplication {
            application {
                receivePipeline.intercept(ApplicationReceivePipeline.Before) {
                    throw IllegalStateException("Testfeil i mottak")
                }
                configureRouting(Environment())
                install(StatusPages) {
                    exception<IllegalStateException> { call, _ ->
                        call.respond(HttpStatusCode.InternalServerError)
                    }
                }
            }

            val htmlApplication = "arbitrary-html-metric-application"
            val imageApplication = "arbitrary-image-metric-application"
            val htmlBefore = OPENHTMLTOPDF_RENDERING_SUMMARY.labels("all", "converthtml").get().count
            val imageBefore = OPENHTMLTOPDF_RENDERING_SUMMARY.labels("all", "convertjpeg").get().count

            val htmlResponse = client.post("/api/v1/genpdf/html/$htmlApplication") {
                setBody(
                    TextContent(
                        "<html><body>metrics test</body></html>",
                        contentType = ContentType.Text.Html,
                    ),
                )
            }
            assertEquals(HttpStatusCode.InternalServerError, htmlResponse.status)

            val imageResponse = client.post("/api/v1/genpdf/image/$imageApplication") {
                setBody(
                    TextContent(
                        "not an image",
                        contentType = ContentType.Text.Plain,
                    ),
                )
            }
            assertEquals(HttpStatusCode.UnsupportedMediaType, imageResponse.status)

            assertEquals(
                htmlBefore + 1.0,
                OPENHTMLTOPDF_RENDERING_SUMMARY.labels("all", "converthtml").get().count,
            )
            assertEquals(
                imageBefore + 1.0,
                OPENHTMLTOPDF_RENDERING_SUMMARY.labels("all", "convertjpeg").get().count,
            )
            val renderingLabels = metricSamples()
                .filter { it.labelValues.contains("converthtml") || it.labelValues.contains("convertjpeg") }
                .flatMap { it.labelValues }
            assertFalse(renderingLabels.contains(htmlApplication))
            assertFalse(renderingLabels.contains(imageApplication))
        }
    }

    private fun requestCount(label: String): Double = requestSamples()
        .firstOrNull { it.labelValues == listOf(label) }
        ?.value ?: 0.0

    private fun requestCountContaining(path: String): Double = requestSamples()
        .filter { it.labelValues.single().contains(path) }
        .sumOf { it.value }

    private fun requestPathLabels(): List<String> = requestSamples().map { it.labelValues.single() }

    private fun requestSamples(): List<Collector.MetricFamilySamples.Sample> = metricSamples().filter {
        it.name.endsWith("_count") && it.labelNames == listOf("path")
    }

    private fun metricSamples(): List<Collector.MetricFamilySamples.Sample> = CollectorRegistry.defaultRegistry
        .metricFamilySamples()
        .asSequence()
        .flatMap { it.samples.asSequence() }
        .toList()
}
