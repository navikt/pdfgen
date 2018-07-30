package no.nav.pdfgen

import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
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
    val client = OkHttpClient()
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
                val requestBody = RequestBody.create(MediaType.parse("application/json"), json)
                val request = Request.Builder()
                        .url("http://localhost:$applicationPort/api/v1/genpdf/$applicationName/$templateName")
                        .post(requestBody)
                        .build()

                val response = client.newCall(request).execute()
                response.isSuccessful shouldEqual true
                response shouldNotEqual null
                response.body() shouldNotEqual null
                val bytes = response.body()!!.bytes()
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
                val requestBody = RequestBody.create(MediaType.parse("application/json"), json)
                val request = Request.Builder()
                        .url("http://localhost:$applicationPort/api/v1/genpdf/$applicationName/$templateName")
                        .post(requestBody)
                        .build()

                val response = client.newCall(request).execute()
                response.isSuccessful shouldEqual true
                response shouldNotEqual null
                response.body() shouldNotEqual null
                val bytes = response.body()!!.bytes()
                Files.write(Paths.get("out", "${it.first}-${it.second}.pdf"), bytes)
                response.close()
            }
        }
    }

    xdescribe("Simple performance test") {
        templates.map { it.key }.forEach {
            val (applicationName, templateName) = it
            val json = javaClass.getResourceAsStream("/data/$applicationName/$templateName.json")?.use {
                IOUtils.toByteArray(it)
            } ?: "{}".toByteArray(Charsets.UTF_8)
            val requestBody = RequestBody.create(MediaType.parse("application/json"), json)
            val request = Request.Builder()
                    .url("http://localhost:$applicationPort/api/v1/genpdf/$applicationName/$templateName")
                    .post(requestBody)
                    .build()
            for (i in 0..19) {
                val response = client.newCall(request).execute()
                response.isSuccessful shouldEqual true
                response.close()
            }
            it("$templateName for $applicationName performs fine") {
                val startTime = System.currentTimeMillis()
                for (i in 0..99) {
                    val response = client.newCall(request).execute()
                    response.isSuccessful shouldEqual true
                    response.close()
                }
                println("Performance testing $templateName for $applicationName took ${System.currentTimeMillis() - startTime}ms")
            }
        }
    }
})
