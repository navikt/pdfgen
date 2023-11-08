package no.nav.pdfgen

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.testcontainers.images.builder.ImageFromDockerfile
import org.testcontainers.containers.GenericContainer
import java.nio.file.Paths

internal class DockerImageTest {
    private var pdfgen: GenericContainer =
    GenericContainer(
     ImageFromDockerfile()
        .withFileFromPath("/", Paths.get("Dockerfile"))
        ).apply {
            start()
         }
    @Test
    internal fun `Test Dockerfile`() {
        Assertions.assertEquals(true, pdfgen.isDeleteOnExit)
    }
}
