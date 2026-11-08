package no.nav.pdfgen

import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.contentType
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
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.pdfgen.application.metrics.configureMonitoring
import no.nav.pdfgen.application.metrics.createPrometheusMeterRegistry
import no.nav.pdfgen.core.PDFGenCore
import no.nav.pdfgen.plugins.configureContentNegotiation
import no.nav.pdfgen.plugins.configureHealthAndMetrics
import no.nav.pdfgen.plugins.configureRouting
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.verapdf.gf.foundry.VeraGreenfieldFoundryProvider
import no.nav.pdfgen.core.Environment as PDFGenCoreEnvironment

internal class MetricsTest {
    @Nested
    inner class KtorHttpMetrics {
        @Test
        internal fun `Ktor HTTP timer should use route patterns and count failures exactly once`() {
            // Arrange
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

                // Act
                val matchedStatuses = (0 until 50).map { index ->
                    client.get("/metrics-cardinality/value-$index").status
                }
                val unmatchedStatuses = (0 until 50).map { index ->
                    client.get("/unmatched-metrics-$index/deep/path").status
                }
                val failedResponse = client.get("/metrics-failure/request-specific-value")
                val healthResponse = client.get("/internal/is_alive")
                val healthBody = healthResponse.bodyAsText()

                // Assert
                assertTrue(matchedStatuses.all { it == HttpStatusCode.OK })
                assertTrue(unmatchedStatuses.all { it == HttpStatusCode.NotFound })
                assertEquals(HttpStatusCode.InternalServerError, failedResponse.status)
                assertEquals(HttpStatusCode.OK, healthResponse.status)
                assertEquals("I'm alive", healthBody)
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
    }

    @Nested
    inner class PrometheusMetrics {
        @Test
        internal fun `Prometheus endpoint should expose Micrometer metrics`() {
            // Arrange
            val meterRegistry = createPrometheusMeterRegistry()
            val coreEnvironment = PDFGenCoreEnvironment()
            PDFGenCore.init(coreEnvironment, meterRegistry)
            VeraGreenfieldFoundryProvider.initialise()

            try {
                testApplication {
                    application {
                        configureContentNegotiation()
                        configureMonitoring(meterRegistry)
                        configureHealthAndMetrics(meterRegistry)
                        configureRouting(Environment(), meterRegistry)
                    }

                    // Act
                    val pdfResponse = client.post("/api/v1/genpdf/dsopkontroll/kontrollforespoersel") {
                        contentType(ContentType.Application.Json)
                        setBody("{}")
                    }
                    val handlebarsTimer = meterRegistry.find("pdfgen_core.handlebars.rendering").timer()
                    val healthResponse = client.get("/internal/is_alive")
                    val response = client.get("/internal/prometheus")
                    val scrape = response.bodyAsText()
                    val filteredScrapes = listOf("_count", "_sum", "_bucket").map { sampleSuffix ->
                        val sampleName = "pdfgen_core_openhtmltopdf_rendering_seconds$sampleSuffix"
                        val filteredResponse = client.get("/internal/prometheus") {
                            parameter("name[]", sampleName)
                        }
                        Triple(sampleName, filteredResponse.status, filteredResponse.bodyAsText())
                    }

                    // Assert
                    assertEquals(HttpStatusCode.OK, pdfResponse.status)
                    assertNotNull(handlebarsTimer)
                    assertEquals(1L, handlebarsTimer?.count())
                    assertEquals(1.0, renderingCount(meterRegistry, "template"))
                    assertEquals(HttpStatusCode.OK, healthResponse.status)
                    assertEquals(HttpStatusCode.OK, response.status)
                    assertTrue(scrape.contains("jvm_memory_used_bytes"))
                    assertTrue(scrape.contains("jvm_memory_max_bytes"))
                    assertTrue(scrape.contains("ktor_http_server_requests_seconds_count"))
                    assertFalse(scrape.contains("pdfgen_requests_duration_seconds_count"))
                    assertTrue(scrape.contains("pdfgen_core_openhtmltopdf_rendering_seconds_count"))
                    assertTrue(scrape.contains("pdfgen_core_handlebars_rendering_seconds_count"))
                    filteredScrapes.forEach { (sampleName, status, filteredScrape) ->
                        assertEquals(HttpStatusCode.OK, status)
                        assertTrue(filteredScrape.contains("# HELP pdfgen_core_openhtmltopdf_rendering_seconds "))
                        assertTrue(filteredScrape.contains(sampleName))
                    }
                }
            } finally {
                PDFGenCore.init(
                    initialEnvironment = coreEnvironment,
                    meterRegistry = SimpleMeterRegistry(),
                )
            }
        }
    }

    @Nested
    inner class PdfConversionMetrics {
        @Test
        internal fun `Conversion metrics should use a fixed application label and count rejected images`() {
            // Arrange
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

                // Act
                val htmlResponse = client.post("/api/v1/genpdf/html/$htmlApplication") {
                    setBody(
                        TextContent(
                            "<html><body>metrics test</body></html>",
                            contentType = ContentType.Text.Html,
                        ),
                    )
                }
                val imageResponse = client.post("/api/v1/genpdf/image/$imageApplication") {
                    setBody(
                        TextContent(
                            "not an image",
                            contentType = ContentType.Text.Plain,
                        ),
                    )
                }

                // Assert
                assertEquals(HttpStatusCode.InternalServerError, htmlResponse.status)
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
