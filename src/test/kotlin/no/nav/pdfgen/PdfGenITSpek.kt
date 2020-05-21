package no.nav.pdfgen

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.post
import io.ktor.client.response.HttpResponse
import io.ktor.client.response.readBytes
import io.ktor.client.response.readText
import io.ktor.http.ContentType
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import io.ktor.util.KtorExperimentalAPI
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.amshove.kluent.shouldBeGreaterThan
import org.amshove.kluent.shouldBeTrue
import org.amshove.kluent.shouldContain
import org.amshove.kluent.shouldEqual
import org.amshove.kluent.shouldNotBeEmpty
import org.amshove.kluent.shouldNotBeNull
import org.amshove.kluent.shouldNotEqual
import org.apache.pdfbox.io.IOUtils
import org.apache.pdfbox.pdmodel.PDDocument
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import org.spekframework.spek2.style.specification.xdescribe

@KtorExperimentalAPI
object PdfGenITSpek : Spek({
    val applicationPort = getRandomPort()
    val application = initializeApplication(applicationPort)
    val client = HttpClient(CIO)
    val templates = loadTemplates()
    application.start()

    afterGroup {
        application.stop(10, 10, TimeUnit.SECONDS)
    }

    describe("POST to /api/v1/genpdf/{applicationName}/{templateName}") {
        templates.map { it.key }.forEach {
            val (applicationName, templateName) = it
            it("With $templateName for $applicationName results in a valid PDF") {
                val json = javaClass.getResourceAsStream("/data/$applicationName/$templateName.json")
                        ?.readBytes()?.toString(Charsets.UTF_8) ?: "{}"

                val response = runBlocking<HttpResponse> {
                    client.post("http://localhost:$applicationPort/api/v1/genpdf/$applicationName/$templateName") {
                        body = TextContent(json, contentType = ContentType.Application.Json)
                    }
                }
                response.status.isSuccess() shouldEqual true
                val bytes = runBlocking { response.readBytes() }
                bytes shouldNotEqual null
                // Load the document in pdfbox to ensure its valid
                val document = PDDocument.load(bytes)
                document shouldNotEqual null
                document.pages.count shouldBeGreaterThan 0
                println(document.documentInformation.title)
                response.close()
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
                        body = TextContent(json, contentType = ContentType.Application.Json)
                    }
                }

                response.status.isSuccess() shouldEqual true
                val bytes = runBlocking { response.readBytes() }
                bytes shouldNotEqual null
                Files.write(Paths.get("build", "${it.first}-${it.second}.pdf"), bytes)
                response.close()
            }
        }
    }

    describe("Using the HTML convert endpoint") {
        it("Should render a document using default HTML") {
            val response = runBlocking<HttpResponse> {
                client.post("http://localhost:$applicationPort/api/v1/genpdf/html/integration-test") {
                    body = testTemplateIncludedFonts
                }
            }
            response.status.isSuccess() shouldEqual true
            val bytes = runBlocking { response.readBytes() }
            bytes shouldNotEqual null
            Files.write(Paths.get("build", "html.pdf"), bytes)
            // Load the document in pdfbox to ensure its valid
            val document = PDDocument.load(bytes)
            document shouldNotEqual null
            document.pages.count shouldBeGreaterThan 0
            println(document.documentInformation.title)
            response.close()
        }

        it("Should return non OK status code when rendering templates with invalid font names") {
            val response = runBlocking<HttpResponse> {
                client.post("http://localhost:$applicationPort/api/v1/genpdf/html/integration-test") {
                    body = TextContent(testTemplateInvalidFonts, contentType = ContentType.Application.Json)
                }
            }
            response.status.isSuccess() shouldEqual false
        }
    }

    describe("Using the image convert endpoint") {
        mapOf(
                ByteArrayContent(testJpg, ContentType.Image.JPEG) to "jpg.pdf",
                ByteArrayContent(testPng, ContentType.Image.PNG) to "png.pdf"
        ).forEach { (payload, outputFile) ->

            it("Should render a document using input image, $outputFile") {
                runBlocking<HttpResponse> {
                    client.post("http://localhost:$applicationPort/api/v1/genpdf/image/integration-test") {
                        body = payload
                    }
                }.use { response ->
                    response.status.isSuccess().shouldBeTrue()
                    val bytes = runBlocking { response.readBytes() }
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

    describe("Calls to unknown endpoints") {
        it("Should respond with helpful information") {
            runBlocking<HttpResponse> {
                client.config { expectSuccess = false }.post("http://localhost:$applicationPort/whodis")
            }.use { response ->
                response.status.value shouldEqual 404
                val text = runBlocking { response.readText() }
                text shouldContain "Known paths:/api/v1/genpdf"
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

                    val response = client.post<HttpResponse>("http://localhost:$applicationPort/api/v1/genpdf/$applicationName/$templateName") {
                        body = TextContent(json, contentType = ContentType.Application.Json)
                    }
                    response.readBytes() shouldNotEqual null
                    response.status.isSuccess() shouldEqual true
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

                            val response = client.post<HttpResponse>("http://localhost:$applicationPort/api/v1/genpdf/$applicationName/$templateName") {
                                body = TextContent(json, contentType = ContentType.Application.Json)
                            }
                            response.readBytes() shouldNotEqual null
                            response.status.isSuccess() shouldEqual true
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

                        val response = client.post<HttpResponse>("http://localhost:$applicationPort/api/v1/genpdf/$applicationName/$templateName") {
                            body = TextContent(json, contentType = ContentType.Application.Json)
                        }
                        response.readBytes() shouldNotEqual null
                        response.status.isSuccess() shouldEqual true
                    }
                }
                println("Single-thread performance testing $templateName for $applicationName took ${System.currentTimeMillis() - startTime}ms")
            }
        }
    }
})
