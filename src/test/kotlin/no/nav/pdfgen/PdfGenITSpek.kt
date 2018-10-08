package no.nav.pdfgen

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.post
import io.ktor.client.response.HttpResponse
import io.ktor.client.response.readBytes
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.newFixedThreadPoolContext
import kotlinx.coroutines.experimental.runBlocking
import org.amshove.kluent.shouldBeGreaterThan
import org.amshove.kluent.shouldEqual
import org.amshove.kluent.shouldNotEqual
import org.apache.pdfbox.io.IOUtils
import org.apache.pdfbox.pdmodel.PDDocument
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import org.spekframework.spek2.style.specification.xdescribe
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

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
                val json = javaClass.getResourceAsStream("/data/$applicationName/$templateName.json")?.use {
                    IOUtils.toByteArray(it)
                } ?: "{}".toByteArray(Charsets.UTF_8)

                val response = runBlocking<HttpResponse> {
                    client.post("http://localhost:$applicationPort/api/v1/genpdf/$applicationName/$templateName") {
                        contentType(ContentType.Application.Json)
                        body = json
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
                val json = javaClass.getResourceAsStream("/data/$applicationName/$templateName.json")?.use {
                    IOUtils.toByteArray(it)
                } ?: "{}".toByteArray(Charsets.UTF_8)

                val response = runBlocking<HttpResponse> {
                    client.post("http://localhost:$applicationPort/api/v1/genpdf/$applicationName/$templateName") {
                        contentType(ContentType.Application.Json)
                        body = json
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

    xdescribe("Simple performance test") {
        val warmupPasses = 20
        val passes = 100

        val context = newFixedThreadPoolContext(8, "Perftest")
        runBlocking {
            val warmupTemplate = templates.map { it.key }.first()
            (1..warmupPasses).map {
                val (applicationName, templateName) = warmupTemplate
                launch(context) {
                    val json = javaClass.getResourceAsStream("/data/$applicationName/$templateName.json")?.use {
                        IOUtils.toByteArray(it)
                    } ?: "{}".toByteArray(Charsets.UTF_8)

                    val response = client.post<HttpResponse>("http://localhost:$applicationPort/api/v1/genpdf/$applicationName/$templateName") {
                        contentType(ContentType.Application.Json)
                        body = json
                    }
                    response.readBytes() shouldNotEqual null
                    response.status.isSuccess() shouldEqual true
                }
            }.toList().forEach { it.join() }
        }
        templates.map { it.key }.forEach { (applicationName, templateName) ->
            it("$templateName for $applicationName performs fine") {
                val startTime = System.currentTimeMillis()
                val tasks = (1..passes).map {
                    launch(context) {
                        val json = javaClass.getResourceAsStream("/data/$applicationName/$templateName.json")?.use {
                            IOUtils.toByteArray(it)
                        } ?: "{}".toByteArray(Charsets.UTF_8)

                        val response = client.post<HttpResponse>("http://localhost:$applicationPort/api/v1/genpdf/$applicationName/$templateName") {
                            contentType(ContentType.Application.Json)
                            body = json
                        }
                        response.readBytes() shouldNotEqual null
                        response.status.isSuccess() shouldEqual true
                    }
                }.toList()
                runBlocking {
                    tasks.forEach { it.join() }
                }
                println("Performance testing $templateName for $applicationName took ${System.currentTimeMillis() - startTime}ms")
            }
            it("$templateName for $applicationName performs fine with single-thread load") {
                val startTime = System.currentTimeMillis()
                runBlocking {
                    for (i in 1..passes) {
                        val json = javaClass.getResourceAsStream("/data/$applicationName/$templateName.json")?.use {
                            IOUtils.toByteArray(it)
                        } ?: "{}".toByteArray(Charsets.UTF_8)

                        val response = client.post<HttpResponse>("http://localhost:$applicationPort/api/v1/genpdf/$applicationName/$templateName") {
                            contentType(ContentType.Application.Json)
                            body = json
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
