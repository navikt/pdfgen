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
import kotlinx.coroutines.*
import no.nav.pdfgen.template.loadTemplates
import org.amshove.kluent.*
import org.apache.pdfbox.io.IOUtils
import org.apache.pdfbox.pdmodel.PDDocument
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import org.spekframework.spek2.style.specification.xdescribe
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.Executors
import kotlin.io.use

object PdfGenITSpek : Spek({
    val applicationPort = getRandomPort()
    val application = initializeApplication(applicationPort)
    val client = HttpClient(CIO) {
        expectSuccess = false
    }
    val env = Environment()
    val templates = loadTemplates(env)
    val timeoutSeconds: Long = 10
    application.start()

    afterGroup {
        application.stop(timeoutSeconds * 1000, timeoutSeconds * 1000)
    }

    describe("POST to /api/v1/genpdf/{applicationName}/{templateName}") {
        templates.map { it.key }.forEach {
            val (applicationName, templateName) = it
            it("With $templateName for $applicationName results in a valid PDF") {
                val json = javaClass.getResourceAsStream("/data/$applicationName/$templateName.json")
                    ?.readBytes()?.toString(Charsets.UTF_8) ?: "{}"

                val response = runBlocking<HttpResponse> {
                    client.post("http://localhost:$applicationPort/api/v1/genpdf/$applicationName/$templateName") {
                        setBody(TextContent(json, contentType = ContentType.Application.Json))
                    }
                }
                response.status.isSuccess() shouldBeEqualTo true
                val bytes = runBlocking { response.readBytes() }
                bytes shouldNotBeEqualTo null
                // Load the document in pdfbox to ensure it's valid
                val document = PDDocument.load(bytes)
                document shouldNotBeEqualTo null
                document.pages.count shouldBeGreaterThan 0
                println(document.documentInformation.title)
                document.close()
            }
        }
    }

    describe("Generate sample PDFs using test data") {
        templates.map { it.key }.forEach {
            val (applicationName, templateName) = it
            it("$templateName for $applicationName generates a sample PDF") {
                val json = javaClass.getResourceAsStream("/data/$applicationName/$templateName.json")
                    ?.readBytes()?.toString(Charsets.UTF_8) ?: "{}"

                val response = runBlocking<HttpResponse> {
                    client.post("http://localhost:$applicationPort/api/v1/genpdf/$applicationName/$templateName") {
                        setBody(TextContent(json, contentType = ContentType.Application.Json))
                    }
                }

                response.status.isSuccess() shouldBeEqualTo true
                val bytes = runBlocking { response.readBytes() }
                bytes shouldNotBeEqualTo null
                Files.write(Paths.get("build", "${it.first}-${it.second}.pdf"), bytes)
            }
        }
    }

    describe("Using the HTML convert endpoint") {
        it("Should render a document using default HTML") {
            val response = runBlocking<HttpResponse> {
                client.post("http://localhost:$applicationPort/api/v1/genpdf/html/integration-test") {
                    setBody(testTemplateIncludedFonts)
                }
            }
            response.status.isSuccess() shouldBeEqualTo true
            val bytes = runBlocking { response.readBytes() }
            bytes shouldNotBeEqualTo null
            Files.write(Paths.get("build", "html.pdf"), bytes)
            // Load the document in pdfbox to ensure its valid
            val document = PDDocument.load(bytes)
            document shouldNotBeEqualTo null
            document.pages.count shouldBeGreaterThan 0
            println(document.documentInformation.title)
        }

        /* openhtmltopdf seems to strip any input that causes non-conforming PDF/A, so i can't get this test to work :(
        it("Should return non OK status code when rendering templates that result in non-conformant PDF/A") {
            val response = runBlocking<HttpResponse> {
                client.post("http://localhost:$applicationPort/api/v1/genpdf/html/integration-test") {
                    body = TextContent(testTemplateInvalidFonts, contentType = ContentType.Application.Json)
                }
            }
            response.status.isSuccess() shouldBeEqualTo false
        }
        */
    }

    describe("Using the image convert endpoint") {
        mapOf(
            ByteArrayContent(testJpg, ContentType.Image.JPEG) to "jpg.pdf",
            ByteArrayContent(testPng, ContentType.Image.PNG) to "png.pdf"
        ).forEach { (payload, outputFile) ->

            it("Should render a document using input image, $outputFile") {
                runBlocking {
                    runBlocking<HttpStatement> {
                        client.preparePost("http://localhost:$applicationPort/api/v1/genpdf/image/integration-test") {
                            setBody(payload)
                        }
                    }.execute { response ->
                        response.status.isSuccess().shouldBeTrue()
                        val bytes = response.readBytes()
                        bytes.shouldNotBeEmpty()
                        Files.write(Paths.get("build", outputFile), bytes)
                        // Load the document in pdfbox to ensure its valid
                        val document = PDDocument.load(bytes)
                        document.shouldNotBeNull()
                        document.pages.count shouldBeGreaterThan 0
                    }
                }
            }
        }
    }

    describe("Calls to unknown endpoints") {
        it("Should respond with helpful information") {
            runBlocking {
                runBlocking<HttpStatement> {
                    client.config { expectSuccess = false }.preparePost("http://localhost:$applicationPort/whodis")
                }.execute { response ->
                    response.status.value shouldBeEqualTo 404
                    val text = runBlocking { response.bodyAsText() }
                    text shouldContain "Known templates:\n/api/v1/genpdf"
                }
            }
        }
    }

    xdescribe("Simple performance test") {
        val warmupPasses = 20
        val passes = 100

        val context = Executors.newFixedThreadPool(8).asCoroutineDispatcher()
        runBlocking(context) {
            val warmupTemplate = templates.map { it.key }.first()
            (1..warmupPasses).map {
                val (applicationName, templateName) = warmupTemplate
                launch {
                    val json = javaClass.getResourceAsStream("/data/$applicationName/$templateName.json")?.use {
                        IOUtils.toByteArray(it).toString(Charsets.UTF_8)
                    }!!

                    val response =
                        client.preparePost("http://localhost:$applicationPort/api/v1/genpdf/$applicationName/$templateName") {
                            setBody(TextContent(json, contentType = ContentType.Application.Json))
                        }.execute()
                    response.readBytes() shouldNotBeEqualTo null
                    response.status.isSuccess() shouldBeEqualTo true
                }
            }.toList().forEach { it.join() }
        }
        templates.map { it.key }.forEach { (applicationName, templateName) ->
            it("$templateName for $applicationName performs fine") {
                val startTime = System.currentTimeMillis()
                runBlocking(context) {
                    val tasks = (1..passes).map {
                        launch {
                            val json = javaClass.getResourceAsStream("/data/$applicationName/$templateName.json")?.use {
                                IOUtils.toByteArray(it).toString(Charsets.UTF_8)
                            } ?: "{}"

                            val response =
                                client.preparePost("http://localhost:$applicationPort/api/v1/genpdf/$applicationName/$templateName") {
                                    setBody(TextContent(json, contentType = ContentType.Application.Json))
                                }.execute()
                            response.readBytes() shouldNotBeEqualTo null
                            response.status.isSuccess() shouldBeEqualTo true
                        }
                    }.toList()
                    tasks.forEach { it.join() }
                }
                println("Performance testing $templateName for $applicationName took ${System.currentTimeMillis() - startTime}ms")
            }
            it("$templateName for $applicationName performs fine with single-thread load") {
                val startTime = System.currentTimeMillis()
                runBlocking {
                    for (i in 1..passes) {
                        val json = javaClass.getResourceAsStream("/data/$applicationName/$templateName.json")?.use {
                            IOUtils.toByteArray(it).toString(Charsets.UTF_8)
                        } ?: "{}"

                        val response =
                            client.preparePost("http://localhost:$applicationPort/api/v1/genpdf/$applicationName/$templateName") {
                                setBody(TextContent(json, contentType = ContentType.Application.Json))
                            }.execute()
                        response.readBytes() shouldNotBeEqualTo null
                        response.status.isSuccess() shouldBeEqualTo true
                    }
                }
                println("Single-thread performance testing $templateName for $applicationName took ${System.currentTimeMillis() - startTime}ms")
            }
        }
    }
})
