@file:Suppress("BlockingMethodInNonBlockingContext")

package no.nav.pdfgen

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.post
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.*
import io.ktor.http.ContentType
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.Executors
import kotlinx.coroutines.*
import no.nav.pdfgen.core.Environment
import org.apache.pdfbox.Loader
import org.apache.pdfbox.io.IOUtils
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

internal class PdfGenITest {
    private val applicationPort = getRandomPort()
    private val application = initializeApplication(applicationPort)
    private val client = HttpClient(CIO) { expectSuccess = false }
    private val templates = Environment().templates
    private val timeoutSeconds: Long = 10

    @AfterEach
    fun stop() {
        application.stop(timeoutSeconds * 1000, timeoutSeconds * 1000)
    }

    @Test
    internal fun post_to_api_v1_genpdf_applicationName_templateName() {
        application.start()
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

                val response =
                    runBlocking {
                        client.post(
                            "http://localhost:$applicationPort/api/v1/genpdf/$applicationName/$templateName"
                        ) {
                            setBody(TextContent(json, contentType = ContentType.Application.Json))
                        }
                    }
                assertEquals(true, response.status.isSuccess())
                val bytes = runBlocking { response.readBytes() }
                assertNotEquals(null, bytes)
                // Load the document in pdfbox to ensure it's valid
                val document = Loader.loadPDF(bytes)
                assertNotEquals(null, document)
                assertEquals(true, document.pages.count > 0)
                println(document.documentInformation.title)
                document.close()
            }
    }

    @Test
    internal fun `Generate sample PDFs using test data`() {
        application.start()
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
                            "http://localhost:$applicationPort/api/v1/genpdf/$applicationName/$templateName"
                        ) {
                            setBody(TextContent(json, contentType = ContentType.Application.Json))
                        }
                    }
                assertEquals(true, response.status.isSuccess())
                val bytes = runBlocking { response.readBytes() }
                assertNotEquals(null, bytes)
                Files.write(Paths.get("build", "${it.first}-${it.second}.pdf"), bytes)
            }
    }

    @Test
    internal fun `Using the HTML convert endpoint should render a document using default HTML`() {
        application.start()
        val response = runBlocking {
            client.post("http://localhost:$applicationPort/api/v1/genpdf/html/integration-test") {
                setBody(testTemplateIncludedFonts)
            }
        }
        assertEquals(true, response.status.isSuccess())
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

    /* openhtmltopdf seems to strip any input that causes non-conforming PDF/A, so i can't get this test to work :(
  @Test
  internal fun `Using the HTML convert endpoint should render a invalid document using default HTML`() {
      application.start()
      val response = runBlocking {
          client.post("http://localhost:$applicationPort/api/v1/genpdf/html/integration-test") {
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
  */

    @Test
    internal fun `Using the image convert endpoint Should render a document using input image`() {
        application.start()
        mapOf(
                ByteArrayContent(testJpg, ContentType.Image.JPEG) to "jpg.pdf",
                ByteArrayContent(testPng, ContentType.Image.PNG) to "png.pdf",
            )
            .forEach { (payload, outputFile) ->
                println("Should render a document using input image, $outputFile")
                runBlocking {
                    runBlocking {
                            client.preparePost(
                                "http://localhost:$applicationPort/api/v1/genpdf/image/integration-test"
                            ) {
                                setBody(payload)
                            }
                        }
                        .execute { response ->
                            assertEquals(true, response.status.isSuccess())
                            val bytes = response.readBytes()
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

    @Test
    internal fun `Calls to unknown endpoints should respond with helpful information`() {
        application.start()
        runBlocking {
            runBlocking {
                    client
                        .config { expectSuccess = false }
                        .preparePost("http://localhost:$applicationPort/whodis")
                }
                .execute { response ->
                    assertEquals(404, response.status.value)
                    val text = runBlocking { response.bodyAsText() }
                    assertEquals(true, text.contains("Known templates:\n/api/v1/genpdf"))
                }
        }
    }

    @Test
    internal fun `Simple performance test full multiple-threads`() {
        application.start()
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
                                                "/data/$applicationName/$templateName.json"
                                            )
                                            ?.use {
                                                IOUtils.toByteArray(it).toString(Charsets.UTF_8)
                                            }
                                            ?: "{}"

                                    val response =
                                        client
                                            .preparePost(
                                                "http://localhost:$applicationPort/api/v1/genpdf/$applicationName/$templateName"
                                            ) {
                                                setBody(
                                                    TextContent(
                                                        json,
                                                        contentType = ContentType.Application.Json
                                                    )
                                                )
                                            }
                                            .execute()
                                    assertNotEquals(null, response.readBytes())
                                    assertEquals(true, response.status.isSuccess())
                                }
                            }
                            .toList()
                    tasks.forEach { it.join() }
                }
                println(
                    "Multiple-threads performance testing $templateName for $applicationName took ${System.currentTimeMillis() - startTime}ms"
                )
            }
    }

    @Test
    internal fun `Simple performance test full single-thread`() {
        application.start()
        val passes = 40

        templates
            .map { it.key }
            .forEach { (applicationName, templateName) ->
                println("$templateName for $applicationName performs fine with single-thread load")
                val startTime = System.currentTimeMillis()
                runBlocking {
                    for (i in 1..passes) {
                        val json =
                            javaClass
                                .getResourceAsStream("/data/$applicationName/$templateName.json")
                                ?.use { IOUtils.toByteArray(it).toString(Charsets.UTF_8) }
                                ?: "{}"

                        val response =
                            client
                                .preparePost(
                                    "http://localhost:$applicationPort/api/v1/genpdf/$applicationName/$templateName"
                                ) {
                                    setBody(
                                        TextContent(
                                            json,
                                            contentType = ContentType.Application.Json
                                        )
                                    )
                                }
                                .execute()
                        assertNotEquals(null, response.readBytes())
                        assertEquals(true, response.status.isSuccess())
                    }
                }
                println(
                    "Single-thread performance testing $templateName for $applicationName took ${System.currentTimeMillis() - startTime}ms"
                )
            }
    }
}
