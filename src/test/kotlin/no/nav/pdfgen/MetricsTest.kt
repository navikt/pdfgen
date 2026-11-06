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
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import no.nav.pdfgen.application.metrics.configureMonitoring
import no.nav.pdfgen.application.metrics.createPrometheusMeterRegistry
import no.nav.pdfgen.application.metrics.recordPdfRendering
import no.nav.pdfgen.plugins.configureNais
import no.nav.pdfgen.plugins.configureRouting
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class MetricsTest {
    @Test
    internal fun `Ktor HTTP timer should use route patterns and count failures exactly once`() {
        val meterRegistry = createPrometheusMeterRegistry()
        testApplication {
            application {
                configureMonitoring(meterRegistry)
                configureRouting(Environment(), meterRegistry)
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
            val matchedBefore = requestCountContainingRoute(meterRegistry, parameterizedPath)
            val unmatchedBefore = requestCountForRoute(meterRegistry, "n/a")
            val failedBefore = requestCountContainingRoute(meterRegistry, "/metrics-failure/{identifier}")
            val healthBefore = requestCountContainingRoute(meterRegistry, "/internal/is_alive")

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

            assertEquals(50.0, requestCountContainingRoute(meterRegistry, parameterizedPath) - matchedBefore)
            assertEquals(
                1,
                requestRouteLabels(meterRegistry).filter { it.contains("/metrics-cardinality") }.distinct().size,
                "Ulike parameterverdier skal dele én rutemal som etikett",
            )
            assertTrue(requestCountForRoute(meterRegistry, "n/a") - unmatchedBefore >= 50.0)
            assertFalse(requestRouteLabels(meterRegistry).any { it.contains("/unmatched-metrics-") })
            assertEquals(
                1.0,
                requestCountContainingRoute(meterRegistry, "/metrics-failure/{identifier}") - failedBefore,
                "Feilende håndterere skal telles nøyaktig én gang",
            )
            assertEquals(1.0, requestCountContainingRoute(meterRegistry, "/internal/is_alive") - healthBefore)
            assertFalse(requestRouteLabels(meterRegistry).any { it.contains("request-specific-value") })
        }
    }

    @Test
    internal fun `Prometheus endpoint should expose Micrometer metrics`() {
        val meterRegistry = createPrometheusMeterRegistry()
        recordPdfRendering(meterRegistry, "converthtml", 10_000_000)

        testApplication {
            application {
                configureMonitoring(meterRegistry)
                configureNais(ApplicationState(alive = true, ready = true), meterRegistry)
                configureRouting(Environment(), meterRegistry)
            }

            val healthResponse = client.get("/internal/is_alive")
            assertEquals(HttpStatusCode.OK, healthResponse.status)

            val response = client.get("/internal/prometheus")
            assertEquals(HttpStatusCode.OK, response.status)
            val scrape = response.bodyAsText()

            assertTrue(scrape.contains("jvm_memory_used_bytes"))
            assertTrue(scrape.contains("jvm_memory_max_bytes"))
            assertTrue(scrape.contains("ktor_http_server_requests_seconds_count"))
            assertFalse(scrape.contains("pdfgen_requests_duration_seconds_count"))
            assertTrue(scrape.contains("pdfgen_core_openhtmltopdf_rendering_seconds_count"))
            assertFalse(scrape.contains("pdfgen_core_handlebars_rendering_count"))
        }
    }

    @Test
    internal fun `Conversion metrics should use a fixed application label and count rejected images`() {
        val meterRegistry = createPrometheusMeterRegistry()
        testApplication {
            application {
                receivePipeline.intercept(ApplicationReceivePipeline.Before) {
                    throw IllegalStateException("Testfeil i mottak")
                }
                configureMonitoring(meterRegistry)
                configureRouting(Environment(), meterRegistry)
                install(StatusPages) {
                    exception<IllegalStateException> { call, _ ->
                        call.respond(HttpStatusCode.InternalServerError)
                    }
                }
            }

            val htmlApplication = "arbitrary-html-metric-application"
            val imageApplication = "arbitrary-image-metric-application"
            val htmlBefore = renderingCount(meterRegistry, "converthtml")
            val imageBefore = renderingCount(meterRegistry, "convertjpeg")

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
                renderingCount(meterRegistry, "converthtml"),
            )
            assertEquals(
                imageBefore + 1.0,
                renderingCount(meterRegistry, "convertjpeg"),
            )
            val renderingLabels = meterRegistry.meters
                .filterIsInstance<Timer>()
                .filter { it.id.name == "pdfgen_core.openhtmltopdf.rendering" }
                .flatMap { timer -> timer.id.tags.map { it.value } }
            assertFalse(renderingLabels.contains(htmlApplication))
            assertFalse(renderingLabels.contains(imageApplication))
        }
    }

    private fun renderingCount(
        meterRegistry: MeterRegistry,
        templateType: String,
    ): Double = meterRegistry
        .find("pdfgen_core.openhtmltopdf.rendering")
        .tag("application_name", "all")
        .tag("template_type", templateType)
        .timer()
        ?.count()
        ?.toDouble()
        ?: 0.0

    private fun requestCountForRoute(
        meterRegistry: MeterRegistry,
        route: String,
    ): Double = requestTimers(meterRegistry)
        .filter { it.id.getTag("route") == route }
        .sumOf { it.count().toDouble() }

    private fun requestCountContainingRoute(
        meterRegistry: MeterRegistry,
        route: String,
    ): Double = requestTimers(meterRegistry)
        .filter { it.id.getTag("route")?.contains(route) == true }
        .sumOf { it.count().toDouble() }

    private fun requestRouteLabels(meterRegistry: MeterRegistry): List<String> = requestTimers(meterRegistry)
        .mapNotNull { it.id.getTag("route") }

    private fun requestTimers(meterRegistry: MeterRegistry): List<Timer> = meterRegistry.meters
        .filterIsInstance<Timer>()
        .filter { it.id.name == "ktor.http.server.requests" }
}
