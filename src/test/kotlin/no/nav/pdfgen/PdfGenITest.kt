@file:Suppress("BlockingMethodInNonBlockingContext")

package no.nav.pdfgen

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.testing.*
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.Executors
import kotlinx.coroutines.*
import no.nav.pdfgen.core.Environment as PDFGenCoreEnvironment
import no.nav.pdfgen.plugins.configureRouting
import org.apache.pdfbox.Loader
import org.apache.pdfbox.io.IOUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

internal class PdfGenITest {
    private val templates = PDFGenCoreEnvironment().templates

    @Test
    internal fun post_to_api_v1_genpdf_applicationName_templateName() {
        testApplication {
            application { module() }
            // api path /api/v1/genpdf/{applicationName}/{templateName}
            templates
                .map { it.key }
                .forEach {
                    val (applicationName, templateName) = it
                    println("With $templateName for $applicationName results in a valid PDF")
                    val json =
                        javaClass
                            .getResourceAsStream("/data/$applicationName/$templateName.json")
                            ?.readBytes()
                            ?.toString(Charsets.UTF_8)
                            ?: "{}"

                    val response = runBlocking {
                        client.post(
                            "/api/v1/genpdf/$applicationName/$templateName",
                        ) {
                            setBody(
                                TextContent(
                                    json,
                                    contentType = ContentType.Application.Json,
                                ),
                            )
                        }
                    }
                    assertEquals(true, response.status.isSuccess())
                    val bytes = runBlocking { response.readRawBytes() }
                    assertNotEquals(null, bytes)
                    // Load the document in pdfbox to ensure it's valid
                    val document = Loader.loadPDF(bytes)
                    assertNotEquals(null, document)
                    assertEquals(true, document.pages.count > 0)
                    println(document.documentInformation.title)
                    document.close()
                }
        }
    }

    @Test
    internal fun post_to_api_v1_genhtml_applicationName_templateName() {

        val templates = PDFGenCoreEnvironment().templates
        val environment =
            Environment(
                disablePdfGet = false,
                enableHtmlEndpoint = true,
            )

        testApplication {
            application {
                module()
                configureRouting(environment = environment)
            }
            // api path /api/v1/genhtml/{applicationName}/{templateName}
            templates
                .map { it.key }
                .forEach {
                    val (applicationName, templateName) = it
                    println("With $templateName for $applicationName results in a valid html")
                    val json =
                        javaClass
                            .getResourceAsStream("/data/$applicationName/$templateName.json")
                            ?.readBytes()
                            ?.toString(Charsets.UTF_8)
                            ?: "{}"

                    val response = runBlocking {
                        client.post(
                            "/api/v1/genhtml/$applicationName/$templateName",
                        ) {
                            setBody(
                                TextContent(
                                    json,
                                    contentType = ContentType.Application.Json,
                                ),
                            )
                        }
                    }
                    assertEquals(true, response.status.isSuccess())
                    assertEquals(true, response.bodyAsText().contains("<html>"))
                }
        }
    }

    @Test
    internal fun `Generate sample PDFs using test data`() {
        testApplication {
            application { module() }
            templates
                .map { it.key }
                .forEach {
                    val (applicationName, templateName) = it
                    println("$templateName for $applicationName generates a sample PDF")
                    val json =
                        javaClass
                            .getResourceAsStream("/data/$applicationName/$templateName.json")
                            ?.readBytes()
                            ?.toString(Charsets.UTF_8)
                            ?: "{}"

                    val response =
                        runBlocking<HttpResponse> {
                            client.post(
                                "/api/v1/genpdf/$applicationName/$templateName",
                            ) {
                                setBody(
                                    TextContent(
                                        json,
                                        contentType = ContentType.Application.Json,
                                    ),
                                )
                            }
                        }
                    assertEquals(true, response.status.isSuccess())
                    val bytes = runBlocking { response.readRawBytes() }
                    assertNotEquals(null, bytes)
                    Files.write(Paths.get("build", "${it.first}-${it.second}.pdf"), bytes)
                }
        }
    }

    @Test
    internal fun `Using the HTML convert endpoint should render a document using default HTML`() {
        testApplication {
            application { module() }
            val response = runBlocking {
                client.post("/api/v1/genpdf/html/integration-test") {
                    setBody(testTemplateIncludedFonts)
                }
            }
            assertEquals(true, response.status.isSuccess())
            val bytes = runBlocking { response.readRawBytes() }
            assertNotEquals(null, bytes)
            Files.write(Paths.get("build", "html.pdf"), bytes)
            // Load the document in pdfbox to ensure its valid
            val document = Loader.loadPDF(bytes)
            assertNotEquals(null, document)
            assertEquals(true, document.pages.count > 0)
            println(document.documentInformation.title)
            document.close()
        }
    }

    /* openhtmltopdf seems to strip any input that causes non-conforming PDF/A, so i can't get this test to work :(
    @Test
    internal fun `Using the HTML convert endpoint should render a invalid document using default HTML`() {
        testApplication {
            application {
                module()
            }
            val response = runBlocking {
                client.post("http://0.0.0.0:$randomApplicationPort/api/v1/genpdf/html/integration-test") {
                    setBody(testTemplateInvalidFonts)
                }
            }
            assertEquals(false, response.status.isSuccess())
            val bytes = runBlocking { response.readBytes() }
            assertNotEquals(null, bytes)
            Files.write(Paths.get("build", "html.pdf"), bytes)
            // Load the document in pdfbox to ensure its valid
            val document = Loader.loadPDF(bytes)
            assertNotEquals(null, document)
            assertEquals(true, document.pages.count > 0)
            println(document.documentInformation.title)
            document.close()
        }
    }

     */

    @Test
    internal fun `Using the image convert endpoint Should render a document using input image`() {
        testApplication {
            application { module() }
            mapOf(
                    ByteArrayContent(testJpg, ContentType.Image.JPEG) to "jpg.pdf",
                    ByteArrayContent(testPng, ContentType.Image.PNG) to "png.pdf",
                )
                .forEach { (payload, outputFile) ->
                    println("Should render a document using input image, $outputFile")
                    runBlocking {
                        runBlocking {
                                client.preparePost(
                                    "/api/v1/genpdf/image/integration-test",
                                ) {
                                    setBody(payload)
                                }
                            }
                            .execute { response ->
                                assertEquals(true, response.status.isSuccess())
                                val bytes = response.readRawBytes()
                                assertEquals(false, bytes.isEmpty())
                                Files.write(Paths.get("build", outputFile), bytes)
                                // Load the document in pdfbox to ensure its valid
                                val document = Loader.loadPDF(bytes)
                                assertNotEquals(null, document)
                                assertEquals(true, document.pages.count > 0)
                                document.close()
                            }
                    }
                }
        }
    }

    @Test
    internal fun `Calls to unknown endpoints should respond with helpful information`() {
        testApplication {
            application { module() }
            runBlocking {
                runBlocking { client.config { expectSuccess = false }.preparePost("/whodis") }
                    .execute { response ->
                        assertEquals(404, response.status.value)
                        val text = runBlocking { response.bodyAsText() }
                        assertEquals(true, text.contains("Known templates:\n/api/v1/genpdf"))
                    }
            }
        }
    }

    @Test
    internal fun `Simple performance test full multiple-threads`() {
        testApplication {
            application { module() }
            val passes = 20

            val context = Executors.newFixedThreadPool(8).asCoroutineDispatcher()
            templates
                .map { it.key }
                .forEach { (applicationName, templateName) ->
                    println("$templateName for $applicationName performs fine")
                    val startTime = System.currentTimeMillis()
                    runBlocking(context) {
                        val tasks =
                            (1..passes)
                                .map {
                                    launch {
                                        val json =
                                            javaClass
                                                .getResourceAsStream(
                                                    "/data/$applicationName/$templateName.json",
                                                )
                                                ?.use {
                                                    IOUtils.toByteArray(it).toString(Charsets.UTF_8)
                                                }
                                                ?: "{}"

                                        val response =
                                            client
                                                .preparePost(
                                                    "/api/v1/genpdf/$applicationName/$templateName",
                                                ) {
                                                    setBody(
                                                        TextContent(
                                                            json,
                                                            contentType =
                                                                ContentType.Application.Json,
                                                        ),
                                                    )
                                                }
                                                .execute()
                                        assertNotEquals(null, response.readRawBytes())
                                        assertEquals(true, response.status.isSuccess())
                                    }
                                }
                                .toList()
                        tasks.forEach { it.join() }
                    }
                    println(
                        "Multiple-threads performance testing $templateName for $applicationName took ${System.currentTimeMillis() - startTime}ms",
                    )
                }
        }
    }

    @Test
    internal fun `Simple performance test full single-thread`() {
        testApplication {
            application { module() }
            val passes = 30

            templates
                .map { it.key }
                .forEach { (applicationName, templateName) ->
                    println(
                        "$templateName for $applicationName performs fine with single-thread load",
                    )
                    val startTime = System.currentTimeMillis()
                    runBlocking {
                        for (i in 1..passes) {
                            val json =
                                javaClass
                                    .getResourceAsStream(
                                        "/data/$applicationName/$templateName.json",
                                    )
                                    ?.use { IOUtils.toByteArray(it).toString(Charsets.UTF_8) }
                                    ?: "{}"

                            val response =
                                client
                                    .preparePost(
                                        "/api/v1/genpdf/$applicationName/$templateName",
                                    ) {
                                        setBody(
                                            TextContent(
                                                json,
                                                contentType = ContentType.Application.Json,
                                            ),
                                        )
                                    }
                                    .execute()
                            assertNotEquals(null, response.readRawBytes())
                            assertEquals(true, response.status.isSuccess())
                        }
                    }
                    println(
                        "Single-thread performance testing $templateName for $applicationName took ${System.currentTimeMillis() - startTime}ms",
                    )
                }
        }
    }
}
