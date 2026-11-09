package no.nav.pdfgen

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.images.builder.ImageFromDockerfile
import org.testcontainers.utility.MountableFile
import kotlin.io.path.Path

@Tag("docker-image")
internal class DockerImageTest {
    @Nested
    inner class PackagedStartupTests {
        @Test
        fun `EngineMain starts with group size variables unset`() = runTest {
            // Arrange
            val groupSizeOverrides = emptyMap<String, String>()

            // Act
            val responseStatus = startPackagedApplication(groupSizeOverrides)

            // Assert
            assertEquals(HttpStatusCode.OK, responseStatus)
        }

        @Test
        fun `EngineMain starts with overridden Netty group sizes`() = runTest {
            // Arrange
            val groupSizeOverrides = mapOf(
                "WORKER_GROUP_SIZE" to "2",
                "CONNECTION_GROUP_SIZE" to "1",
                "CALL_GROUP_SIZE" to "2",
            )

            // Act
            val responseStatus = startPackagedApplication(groupSizeOverrides)

            // Assert
            assertEquals(HttpStatusCode.OK, responseStatus)
        }
    }

    private val groupSizeEnvironmentVariables =
        setOf("WORKER_GROUP_SIZE", "CONNECTION_GROUP_SIZE", "CALL_GROUP_SIZE")

    private suspend fun startPackagedApplication(groupSizeOverrides: Map<String, String>): HttpStatusCode =
        GenericContainer(ImageFromDockerfile().withDockerfile(Path("./Dockerfile")))
            .withCopyToContainer(MountableFile.forHostPath("templates"), "/app/templates")
            .withCopyToContainer(MountableFile.forHostPath("fonts"), "/app/fonts")
            .withCopyToContainer(MountableFile.forHostPath("resources"), "/app/resources")
            .withEnv(groupSizeOverrides)
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/internal/is_ready"))
            .use { container ->
                container.start()

                // verify that environment is as expected
                assertEquals(
                    groupSizeOverrides,
                    container.envMap.filterKeys { it in groupSizeEnvironmentVariables },
                )

                HttpClient(CIO).use { client ->
                    client
                        .get(
                            "http://${container.host}:${container.getMappedPort(8080)}/internal/is_ready",
                        ).status
                }
            }
}
