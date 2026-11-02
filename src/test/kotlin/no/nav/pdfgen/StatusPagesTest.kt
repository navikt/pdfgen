package no.nav.pdfgen

import com.fasterxml.jackson.databind.JsonNode
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.server.application.Application
import io.ktor.server.request.receive
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import no.nav.pdfgen.plugins.configureContentNegotiation
import no.nav.pdfgen.plugins.configureStatusPages
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

internal class StatusPagesTest {
    @Test
    internal fun `Ugyldig JSON gir 400 uten å lekke innhold`() = testApplication {
        application { configureTestApplication() }

        val response = client.post("/json") {
            setBody(
                TextContent(
                    """{"sensitive":"PARSE_SECRET","broken":}""",
                    ContentType.Application.Json,
                ),
            )
        }

        val body = response.bodyAsText()
        assertEquals(HttpStatusCode.BadRequest, response.status, "Ugyldig JSON skal gi 400")
        assertEquals("Bad Request", body, "Feilmeldingen skal være fast")
        assertFalse(body.contains("PARSE_SECRET"), "Feilmeldingen skal ikke inneholde forespørselsdata")
    }

    @Test
    internal fun `Ikke-støttet innholdstype gir 415 uten å lekke innhold`() = testApplication {
        application { configureTestApplication() }

        val response = client.post("/json") {
            setBody(TextContent("CONTENT_SECRET", ContentType.Text.Plain))
        }

        val body = response.bodyAsText()
        assertEquals(HttpStatusCode.UnsupportedMediaType, response.status, "Ustøttet innholdstype skal gi 415")
        assertEquals("Unsupported Media Type", body, "Feilmeldingen skal være fast")
        assertFalse(body.contains("CONTENT_SECRET"), "Feilmeldingen skal ikke inneholde forespørselsdata")
    }

    @Test
    internal fun `Uventet feil gir 500 uten å lekke feildetaljer`() = testApplication {
        application { configureTestApplication() }

        val response = client.get("/fail")

        val body = response.bodyAsText()
        assertEquals(HttpStatusCode.InternalServerError, response.status, "Uventet feil skal gi 500")
        assertEquals("Internal Server Error", body, "Feilmeldingen skal være fast")
        assertFalse(body.contains("SERVER_SECRET"), "Feilmeldingen skal ikke inneholde feildetaljer")
    }

    @Test
    internal fun `Gyldig JSON håndteres som før`() = testApplication {
        application { configureTestApplication() }

        val response = client.post("/json") {
            setBody(TextContent("""{"ok":true}""", ContentType.Application.Json))
        }

        assertEquals(HttpStatusCode.OK, response.status, "Gyldig JSON skal behandles")
        assertEquals("""{"ok":true}""", response.bodyAsText(), "JSON-responsen skal være uendret")
    }

    @Test
    internal fun `Ukjent sti beholder den tilpassede 404-responsen`() = testApplication {
        application { configureTestApplication() }

        val response = client.get("/missing")

        assertEquals(HttpStatusCode.NotFound, response.status, "Ukjent sti skal gi 404")
        assertEquals(
            "Unkown path '/missing'. Known templates:\n",
            response.bodyAsText(),
            "Den tilpassede 404-teksten skal beholdes",
        )
    }

    private fun Application.configureTestApplication() {
        configureContentNegotiation()
        configureStatusPages(emptyMap(), Environment(disablePdfGet = true))
        routing {
            post("/json") {
                val json = call.receive<JsonNode>()
                call.respondText(json.toString())
            }
            get("/fail") {
                throw IllegalStateException("SERVER_SECRET")
            }
        }
    }
}
