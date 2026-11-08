package no.nav.pdfgen

import io.ktor.client.request.post
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import io.ktor.server.testing.testApplication
import no.nav.pdfgen.plugins.configureRouting
import no.nav.pdfgen.plugins.configureStatusPages
import org.apache.pdfbox.Loader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths
import no.nav.pdfgen.core.Environment as PDFGenCoreEnvironment

internal class PdfGenITest {
    private val templates = PDFGenCoreEnvironment().templates

    @Nested
    inner class TemplateEndpoints {
        @Test
        fun `returns a valid PDF for every configured template`() {
            // Arrange
            testApplication {
                application { module() }

                templates.map { it.key }.forEach { (applicationName, templateName) ->
                    val json = templateJson(applicationName, templateName)

                    // Act
                    val response = client.post("/api/v1/genpdf/$applicationName/$templateName") {
                        setBody(TextContent(json, contentType = ContentType.Application.Json))
                    }
                    val bytes = response.readRawBytes()

                    // Assert
                    assertTrue(response.status.isSuccess())
                    assertTrue(response.headers["Content-Type"]?.contains("application/pdf") == true)
                    Loader.loadPDF(bytes).use { document ->
                        assertTrue(document.pages.count > 0)
                    }
                }
            }
        }

        @Test
        fun `returns rendered HTML for every configured template`() {
            // Arrange
            val environment = Environment(
                disablePdfGet = false,
                enableHtmlEndpoint = true,
            )
            testApplication {
                application {
                    module()
                    configureRouting(environment = environment)
                }

                templates.map { it.key }.forEach { (applicationName, templateName) ->
                    val json = templateJson(applicationName, templateName)

                    // Act
                    val response = client.post("/api/v1/genhtml/$applicationName/$templateName") {
                        setBody(TextContent(json, contentType = ContentType.Application.Json))
                    }
                    val body = response.bodyAsText()

                    // Assert
                    assertTrue(response.status.isSuccess())
                    assertTrue(response.headers["Content-Type"]?.contains("text/html") == true)
                    assertTrue(body.contains(Regex("<html[\\s>]")))
                }
            }
        }

        @Test
        fun `writes sample PDFs for every configured template`() {
            // Arrange
            testApplication {
                application { module() }

                templates.map { it.key }.forEach { (applicationName, templateName) ->
                    val json = templateJson(applicationName, templateName)

                    // Act
                    val response = client.post("/api/v1/genpdf/$applicationName/$templateName") {
                        setBody(TextContent(json, contentType = ContentType.Application.Json))
                    }
                    val bytes = response.readRawBytes()
                    Files.write(Paths.get("build", "$applicationName-$templateName.pdf"), bytes)

                    // Assert
                    assertTrue(response.status.isSuccess())
                    assertTrue(bytes.isNotEmpty())
                }
            }
        }
    }

    @Nested
    inner class ConversionEndpoints {
        @Test
        fun `converts HTML input into a valid PDF`() {
            // Arrange
            testApplication {
                application { module() }

                // Act
                val response = client.post("/api/v1/genpdf/html/integration-test") {
                    setBody(testTemplateIncludedFonts)
                }
                val bytes = response.readRawBytes()

                // Assert
                assertTrue(response.status.isSuccess())
                assertTrue(bytes.isNotEmpty())
                Files.write(Paths.get("build", "html.pdf"), bytes)
                Loader.loadPDF(bytes).use { document ->
                    assertTrue(document.pages.count > 0)
                }
            }
        }

        @Test
        fun `renders HTML with an unavailable font as a valid PDF`() {
            // Arrange
            testApplication {
                application { module() }

                // Act
                val response = client.post("/api/v1/genpdf/html/integration-test") {
                    setBody(testTemplateInvalidFonts)
                }
                val bytes = response.readRawBytes()

                // Assert
                assertTrue(response.status.isSuccess())
                assertTrue(response.headers["Content-Type"]?.contains("application/pdf") == true)
                assertTrue(bytes.isNotEmpty())
                Loader.loadPDF(bytes).use { document ->
                    assertTrue(document.pages.count > 0)
                }
            }
        }

        @Test
        fun `converts JPEG and PNG inputs into valid PDFs`() {
            // Arrange
            val images = listOf(
                ByteArrayContent(testJpg, ContentType.Image.JPEG) to "jpg.pdf",
                ByteArrayContent(testPng, ContentType.Image.PNG) to "png.pdf",
            )
            testApplication {
                application { module() }

                images.forEach { (payload, outputFile) ->
                    // Act
                    val response = client.post("/api/v1/genpdf/image/integration-test") {
                        setBody(payload)
                    }
                    val bytes = response.readRawBytes()

                    // Assert
                    assertTrue(response.status.isSuccess())
                    assertTrue(response.headers["Content-Type"]?.contains("application/pdf") == true)
                    assertTrue(bytes.isNotEmpty())
                    Files.write(Paths.get("build", outputFile), bytes)
                    Loader.loadPDF(bytes).use { document ->
                        assertTrue(document.pages.count > 0)
                    }
                }
            }
        }
    }

    @Nested
    inner class UnknownEndpoints {
        @Test
        fun `returns helpful information for an unknown PDF endpoint`() {
            // Arrange
            testApplication {
                application { module() }
                val nonSuccessClient = client.config { expectSuccess = false }

                // Act
                val response = nonSuccessClient.preparePost("/whodis").execute()
                val text = response.bodyAsText()

                // Assert
                assertEquals(404, response.status.value)
                assertTrue(text.contains("Known templates:\n/api/v1/genpdf"))
            }
        }

        @Test
        fun `returns helpful information for unknown PDF and HTML endpoints`() {
            // Arrange
            val environment = Environment(
                disablePdfGet = false,
                enableHtmlEndpoint = true,
            )
            testApplication {
                application { configureStatusPages(templates, environment) }
                val nonSuccessClient = client.config { expectSuccess = false }

                // Act
                val response = nonSuccessClient.preparePost("/imnothome").execute()
                val text = response.bodyAsText()

                // Assert
                assertEquals(404, response.status.value)
                assertTrue(text.contains("Known templates:\n/api/v1/genpdf"))
                assertTrue(text.contains("api/v1/genhtml"))
            }
        }
    }

    private fun templateJson(
        applicationName: String,
        templateName: String,
    ): String = getResourceOrNull("/data/$applicationName/$templateName.json") ?: "{}"
}
