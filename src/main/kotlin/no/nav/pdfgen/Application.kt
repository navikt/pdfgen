package no.nav.pdfgen

// Uncommemt to enable debug to file
// import java.io.File
import com.openhtmltopdf.slf4j.Slf4jLogger
import com.openhtmltopdf.util.XRLog

import io.ktor.server.application.*
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

import io.prometheus.client.hotspot.DefaultExports

import no.nav.pdfgen.core.Environment as PDFGenCoreEnvironment
import no.nav.pdfgen.core.PDFGenCore
import no.nav.pdfgen.plugins.configureContentNegotiation
import no.nav.pdfgen.plugins.configureNaisThings
import no.nav.pdfgen.plugins.configureReloadPDFGenCorePlugin

import no.nav.pdfgen.plugins.configureRouting
import no.nav.pdfgen.plugins.configureStatusPages
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.verapdf.gf.foundry.VeraGreenfieldFoundryProvider
import java.util.concurrent.TimeUnit

val log: Logger = LoggerFactory.getLogger("pdfgen")

fun main() {
    DefaultExports.initialize()

    val embeddedServer =
        embeddedServer(
            Netty,
            port = Environment().port,
            module = Application::module,
            configure = {
                responseWriteTimeoutSeconds = 60
            },
        )
    Runtime.getRuntime()
        .addShutdownHook(
            Thread {
                log.info("Shutting down application from shutdown hook")
                embeddedServer.stop(TimeUnit.SECONDS.toMillis(10), TimeUnit.SECONDS.toMillis(10))
            },
        )
    embeddedServer.start(true)
}

fun Application.module() {
    val applicationState = ApplicationState()

    System.setProperty("sun.java2d.cmm", "sun.java2d.cmm.kcms.KcmsServiceProvider")
    VeraGreenfieldFoundryProvider.initialise()

    val environment = Environment()
    val coreEnvironment = PDFGenCoreEnvironment()
    PDFGenCore.init(coreEnvironment)

    val templates = coreEnvironment.templates
    XRLog.setLoggerImpl(Slf4jLogger())

    configureContentNegotiation()
    configureStatusPages(templates)
    configureNaisThings(applicationState)
    configureReloadPDFGenCorePlugin(environment = environment)
    configureRouting(
        environment = environment,
    )
}

data class ApplicationState(
    var alive: Boolean = true,
    var ready: Boolean = true,
)
