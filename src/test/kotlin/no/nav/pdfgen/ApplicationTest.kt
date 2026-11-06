package no.nav.pdfgen

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopPreparing
import io.ktor.server.application.ServerReady
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import no.nav.pdfgen.application.api.nais.registerNaisApi
import no.nav.pdfgen.application.metrics.createPrometheusMeterRegistry
import no.nav.pdfgen.plugins.configureLifecycleHooks
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class ApplicationTest {
    @Test
    internal fun `App is ready only after ServerReady is raised`() {
        testApplication {
            lateinit var raiseApplicationStarted: () -> Any
            lateinit var raiseServerReady: () -> Any
            application {
                routing {
                    val applicationState = ApplicationState()
                    registerNaisApi(applicationState, meterRegistry = createPrometheusMeterRegistry())
                    configureLifecycleHooks(applicationState)
                }
                raiseApplicationStarted = { monitor.raise(ApplicationStarted, this) }
                raiseServerReady = { monitor.raise(ServerReady, this.environment) }
            }

            assertNotReady()
            raiseApplicationStarted()
            assertNotReady()
            raiseServerReady()
            assertReady()
        }
    }

    @Test
    internal fun `App is alive after application is started`() {
        testApplication {
            application {
                routing {
                    val applicationState = ApplicationState()
                    configureLifecycleHooks(applicationState)
                    registerNaisApi(applicationState, meterRegistry = createPrometheusMeterRegistry())
                }
            }
            val response = client.get("/internal/is_alive")
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("I'm alive", response.bodyAsText())
        }
    }

    @Test
    internal fun `Returns ok on is_alive`() {
        testApplication {
            application {
                routing {
                    val applicationState = ApplicationState()
                    applicationState.ready = true
                    applicationState.alive = true
                    registerNaisApi(applicationState, meterRegistry = createPrometheusMeterRegistry())
                }
            }
            val response = client.get("/internal/is_alive")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("I'm alive", response.bodyAsText())
        }
    }

    @Test
    internal fun `Returns internal server error when liveness check fails`() {
        testApplication {
            application {
                routing {
                    val applicationState = ApplicationState()
                    applicationState.ready = false
                    applicationState.alive = false
                    registerNaisApi(applicationState, meterRegistry = createPrometheusMeterRegistry())
                }
            }
            val response = client.get("/internal/is_alive")

            assertEquals(HttpStatusCode.InternalServerError, response.status)
            assertEquals("I'm dead x_x", response.bodyAsText())
        }
    }

    @Test
    internal fun `Returns internal server error when readyness check fails`() {
        testApplication {
            application {
                routing {
                    val applicationState = ApplicationState()
                    applicationState.ready = false
                    applicationState.alive = false
                    registerNaisApi(applicationState, meterRegistry = createPrometheusMeterRegistry())
                }
            }
            assertNotReady()
        }
    }

    @Test
    internal fun `App stops being ready once ApplicationStopPreparing is raised`() {
        testApplication {
            lateinit var raiseApplicationStarted: () -> Any
            lateinit var raiseServerReady: () -> Any
            lateinit var raiseApplicationStopPreparing: () -> Any
            application {
                routing {
                    val applicationState = ApplicationState()
                    registerNaisApi(applicationState, meterRegistry = createPrometheusMeterRegistry())
                    configureLifecycleHooks(applicationState)
                }
                raiseApplicationStarted = { monitor.raise(ApplicationStarted, this) }
                raiseServerReady = { monitor.raise(ServerReady, this.environment) }
                raiseApplicationStopPreparing = { monitor.raise(ApplicationStopPreparing, this.environment) }
            }

            assertNotReady()
            raiseApplicationStarted()
            raiseServerReady()
            assertReady()

            raiseApplicationStopPreparing()
            assertNotReady()
        }
    }

    private suspend fun ApplicationTestBuilder.assertReady() {
        val readyResponse = client.get("/internal/is_ready")
        assertEquals(HttpStatusCode.OK, readyResponse.status)
        assertEquals("I'm ready", readyResponse.bodyAsText())
    }

    private suspend fun ApplicationTestBuilder.assertNotReady() {
        val response = client.get("/internal/is_ready")
        assertEquals(HttpStatusCode.InternalServerError, response.status)
        assertEquals("Please wait! I'm not ready :(", response.bodyAsText())
    }
}
