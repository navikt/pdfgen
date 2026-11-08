package no.nav.pdfgen

import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import no.nav.pdfgen.core.Environment
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.Executors

internal class PdfgenPerformanceTest {
    private val templates = Environment().templates

    @Test
    internal fun `Simple performance test full multiple-threads`() {
        testApplication {
            application { module() }
            val passes = 20

            Executors.newFixedThreadPool(8).asCoroutineDispatcher().use { context ->
                templates
                    .map { it.key }
                    .forEach { (applicationName, templateName) ->
                        println("$templateName for $applicationName performs fine")
                        val startTime = System.currentTimeMillis()
                        withContext(context) {
                            val tasks =
                                (1..passes)
                                    .map {
                                        launch {
                                            val json = getResourceOrNull<String>(
                                                "/data/$applicationName/$templateName.json",
                                            ) ?: "{}"

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
                                                    }.execute()
                                            assertNotEquals(null, response.readRawBytes())
                                            assertEquals(true, response.status.isSuccess())
                                        }
                                    }.toList()
                            tasks.joinAll()
                        }
                        println(
                            "Multiple-threads performance testing $templateName for $applicationName took ${System.currentTimeMillis() - startTime}ms",
                        )
                    }
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
                    repeat(passes) {
                        val json = getResourceOrNull<String>(
                            "/data/$applicationName/$templateName.json",
                        )
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
                                }.execute()
                        assertNotEquals(null, response.readRawBytes())
                        assertEquals(true, response.status.isSuccess())
                    }
                    println(
                        "Single-thread performance testing $templateName for $applicationName took ${System.currentTimeMillis() - startTime}ms",
                    )
                }
        }
    }
}
