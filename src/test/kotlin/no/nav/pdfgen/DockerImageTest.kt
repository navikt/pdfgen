package no.nav.pdfgen

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.testcontainers.images.builder.ImageFromDockerfile
import java.nio.file.Paths

internal class DockerImageTest {
    private var pdfgen: ImageFromDockerfile = ImageFromDockerfile()
        .withFileFromPath("/", Paths.get("Dockerfile"))
    @Test
    internal fun `Test Dockerfile`() {
        Assertions.assertEquals(true, pdfgen.isDeleteOnExit)
    }
}
