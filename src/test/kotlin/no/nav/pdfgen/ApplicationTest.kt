package no.nav.pdfgen

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import no.nav.pdfgen.application.api.nais.registerNaisApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class ApplicationTest {

    @Test
    internal fun `Returns ok on is_alive`() {
        testApplication {
            application {
                routing {
                    val applicationState = ApplicationState()
                    applicationState.ready = true
                    applicationState.alive = true
                    registerNaisApi(applicationState)
                }
            }
            val response = client.get("/internal/is_alive")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("I'm alive", response.bodyAsText())
        }
    }

    @Test
    internal fun `Returns ok in is_ready`() {
        testApplication {
            application {
                routing {
                    val applicationState = ApplicationState()
                    applicationState.ready = true
                    applicationState.alive = true
                    registerNaisApi(applicationState)
                }
            }
            val response = client.get("/internal/is_ready")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("I'm ready", response.bodyAsText())
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
                    registerNaisApi(applicationState)
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
                    registerNaisApi(applicationState)
                }
            }
            val response = client.get("/internal/is_ready")

            assertEquals(HttpStatusCode.InternalServerError, response.status)
            assertEquals("Please wait! I'm not ready :(", response.bodyAsText())
        }
    }
}
