package no.nav.pdfgen

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.testing.*
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.pdfgen.core.Environment
import org.apache.pdfbox.io.IOUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

internal class PdfgenPerformanceTest {

    private val templates = Environment().templates

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
