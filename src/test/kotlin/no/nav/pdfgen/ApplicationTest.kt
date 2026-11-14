package no.nav.pdfgen

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationStopPreparing
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import no.nav.pdfgen.application.api.isReadyKey
import no.nav.pdfgen.application.api.registerHealthApi
import no.nav.pdfgen.application.metrics.createPrometheusMeterRegistry
import no.nav.pdfgen.plugins.configureLifecycleHooks
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

internal class ApplicationTest {
    @Nested
    inner class StartupLoggingTests {
        @Test
        fun `startup log includes configured version`() {
            assertStartupVersion(
                config = MapApplicationConfig("pdfgen.version" to "2.0.126"),
                expectedVersion = "2.0.126",
            )
        }

        @Test
        fun `startup log uses unknown when version is missing`() {
            assertStartupVersion(
                config = MapApplicationConfig(),
                expectedVersion = "unknown",
            )
        }

        private fun assertStartupVersion(
            config: MapApplicationConfig,
            expectedVersion: String,
        ) {
            val appender = ListAppender<ILoggingEvent>().apply { start() }
            val applicationLogger = LoggerFactory.getLogger("pdfgen") as Logger
            applicationLogger.addAppender(appender)

            try {
                testApplication {
                    environment { this.config = config }
                    application { module() }
                    startApplication()
                }

                assertTrue(
                    appender.list.any {
                        it.formattedMessage == "Starting pdfgen version $expectedVersion"
                    },
                )
            } finally {
                applicationLogger.detachAppender(appender)
                appender.stop()
            }
        }
    }

    @Nested
    inner class LivenessTests {
        @Test
        fun `liveness stays healthy during shutdown preparation`() {
            testApplication {
                // Arrange
                lateinit var raiseStopPreparing: () -> Any
                application {
                    routing {
                        registerHealthApi(meterRegistry = createPrometheusMeterRegistry())
                    }
                    configureLifecycleHooks()
                    attributes.put(isReadyKey, true)
                    raiseStopPreparing = {
                        monitor.raise(ApplicationStopPreparing, environment)
                    }
                }
                startApplication()

                // Act
                raiseStopPreparing()
                val response = client.get("/internal/is_alive")

                // Assert
                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals("I'm alive", response.bodyAsText())
            }
        }
    }

    @Nested
    inner class ReadinessTests {
        @Test
        fun `readiness is unavailable until application is ready`() {
            testApplication {
                // Arrange
                application {
                    routing {
                        registerHealthApi(meterRegistry = createPrometheusMeterRegistry())
                    }
                }

                // Act
                val response = client.get("/internal/is_ready")

                // Assert
                assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
                assertEquals("Please wait! I'm not ready :(", response.bodyAsText())
            }
        }

        @Test
        fun `readiness succeeds when application is ready`() {
            testApplication {
                // Arrange
                application {
                    routing {
                        registerHealthApi(meterRegistry = createPrometheusMeterRegistry())
                    }
                    attributes.put(isReadyKey, true)
                }

                // Act
                val response = client.get("/internal/is_ready")

                // Assert
                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals("I'm ready", response.bodyAsText())
            }
        }

        @Test
        fun `readiness becomes unavailable on ApplicationStopPreparing`() {
            testApplication {
                // Arrange
                lateinit var raiseStopPreparing: () -> Any
                application {
                    routing {
                        registerHealthApi(meterRegistry = createPrometheusMeterRegistry())
                    }
                    configureLifecycleHooks()
                    attributes.put(isReadyKey, true)
                    raiseStopPreparing = {
                        monitor.raise(ApplicationStopPreparing, environment)
                    }
                }
                startApplication()

                // Act
                raiseStopPreparing()
                val response = client.get("/internal/is_ready")

                // Assert
                assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
                assertEquals("Please wait! I'm not ready :(", response.bodyAsText())
            }
        }
    }

    @Nested
    inner class EndpointConfigurationTests {
        @Nested
        inner class HtmlEndpointTests {
            @Test
            fun `HTML GET endpoint is registered when enabled`() = testApplication {
                // Arrange
                environment {
                    config = MapApplicationConfig("pdfgen.enableHtmlEndpoint" to "true")
                }
                application { module() }

                // Act
                val response = client.get("/api/v1/genhtml/dsopkontroll/kontrollforespoersel")
                val body = response.bodyAsText()

                // Assert
                assertEquals(HttpStatusCode.OK, response.status)
                assertTrue(body.contains("<html"))
            }

            @Test
            fun `HTML GET endpoint is not registered when disabled`() = testApplication {
                // Arrange
                environment {
                    config = MapApplicationConfig("pdfgen.enableHtmlEndpoint" to "false")
                }
                application { module() }

                // Act
                val response = client.get("/api/v1/genhtml/dsopkontroll/kontrollforespoersel")
                val body = response.bodyAsText()

                // Assert
                assertEquals(HttpStatusCode.NotFound, response.status)
                assertTrue(body.contains("Known templates:"))
                assertFalse(body.contains("Known templates:\n/api/v1/genhtml/"))
            }

            @Test
            fun `HTML GET endpoint is not registered when configuration is unset`() = testApplication {
                // Arrange
                environment {
                    config = MapApplicationConfig()
                }
                application { module() }

                // Act
                val response = client.get("/api/v1/genhtml/dsopkontroll/kontrollforespoersel")
                val body = response.bodyAsText()

                // Assert
                assertEquals(HttpStatusCode.NotFound, response.status)
                assertTrue(body.contains("Known templates:"))
                assertFalse(body.contains("Known templates:\n/api/v1/genhtml/"))
            }
        }

        @Nested
        inner class PdfGetEndpointTests {
            @Test
            fun `PDF GET endpoint is not registered when disabled`() = testApplication {
                // Arrange
                environment {
                    config = MapApplicationConfig("pdfgen.disablePdfGet" to "true")
                }
                application { module() }

                // Act
                val response = client.get("/api/v1/genpdf/dsopkontroll/kontrollforespoersel")
                val body = response.bodyAsText()

                // Assert
                assertEquals(HttpStatusCode.NotFound, response.status)
                assertTrue(body.contains("Known templates:"))
            }

            @Test
            fun `PDF GET endpoint is registered when disablePdfGet is false`() = testApplication {
                // Arrange
                environment {
                    config = MapApplicationConfig("pdfgen.disablePdfGet" to "false")
                }
                application { module() }

                // Act
                val response = client.get("/api/v1/genpdf/dsopkontroll/kontrollforespoersel")

                // Assert
                assertEquals(HttpStatusCode.OK, response.status)
                assertTrue(response.headers["Content-Type"]?.contains("application/pdf") == true)
            }

            @Test
            fun `PDF GET endpoint is registered when disablePdfGet is unset`() = testApplication {
                // Arrange
                environment {
                    config = MapApplicationConfig()
                }
                application { module() }

                // Act
                val response = client.get("/api/v1/genpdf/dsopkontroll/kontrollforespoersel")

                // Assert
                assertEquals(HttpStatusCode.OK, response.status)
                assertTrue(response.headers["Content-Type"]?.contains("application/pdf") == true)
            }
        }
    }
}
