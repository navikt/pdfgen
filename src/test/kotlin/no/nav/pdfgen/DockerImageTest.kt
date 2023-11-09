package no.nav.pdfgen

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import kotlin.io.path.Path
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.images.builder.ImageFromDockerfile

internal class DockerImageTest {
    private val network = Network.newNetwork()
    private var pdfgenContainer =
        GenericContainer(
                ImageFromDockerfile().withDockerfile(Path("./Dockerfile")),
            )
            .withNetwork(network)
            .withExposedPorts(8080)
            .apply { start() }

    @Test
    internal fun `Test Dockerfile`() {

        runBlocking {
            Thread.sleep(1000)
            val pdfgenContainerUrlIsReady = pdfgenContainer.buildUrl("internal/is_ready")

            val httpClient =
                HttpClient(CIO) {
                    install(ContentNegotiation) {
                        jackson {
                            registerKotlinModule()
                            registerModule(JavaTimeModule())
                            configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                        }
                    }
                }

            val response =
                httpClient.get(pdfgenContainerUrlIsReady) { accept(ContentType.Application.Json) }

            Assertions.assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    private fun GenericContainer<*>.buildUrl(url: String) =
        "http://${this.host}:${this.getMappedPort(8080)}/$url"
}
