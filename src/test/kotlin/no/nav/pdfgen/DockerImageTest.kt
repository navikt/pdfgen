package no.nav.pdfgen

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.images.builder.ImageFromDockerfile
import org.testcontainers.utility.MountableFile
import kotlin.io.path.Path

internal class DockerImageTest {
    @Test
    internal fun `Test Dockerfile`() {
        Network.newNetwork().use { network ->
            GenericContainer(ImageFromDockerfile().withDockerfile(Path("./Dockerfile")))
                .withNetwork(network)
                .withCopyToContainer(MountableFile.forHostPath("templates"), "/app/templates")
                .withCopyToContainer(MountableFile.forHostPath("fonts"), "/app/fonts")
                .withCopyToContainer(MountableFile.forHostPath("resources"), "/app/resources")
                .withExposedPorts(8080)
                .waitingFor(Wait.forHttp("/internal/is_ready"))
                .use { pdfgenContainer ->
                    pdfgenContainer.start()

                    runBlocking {
                        val pdfgenContainerUrlIsReady = pdfgenContainer.buildUrl("internal/is_ready")

                        HttpClient(CIO) {
                            install(ContentNegotiation) {
                                jackson {
                                    registerKotlinModule()
                                    registerModule(JavaTimeModule())
                                    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                                    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                                }
                            }
                        }.use { httpClient ->
                            val response =
                                httpClient.get(pdfgenContainerUrlIsReady) {
                                    accept(ContentType.Application.Json)
                                }

                            Assertions.assertEquals(HttpStatusCode.OK, response.status)
                        }
                    }
                }
        }
    }

    private fun GenericContainer<*>.buildUrl(url: String) = "http://${this.host}:${this.getMappedPort(8080)}/$url"
}
