package no.nav.pdfgen

import com.openhtmltopdf.slf4j.Slf4jLogger
import com.openhtmltopdf.util.XRLog
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.Netty
import io.prometheus.client.hotspot.DefaultExports
import no.nav.pdfgen.core.Environment as PDFGenCoreEnvironment
import no.nav.pdfgen.core.PDFGenCore
import no.nav.pdfgen.plugins.configureContentNegotiation
import no.nav.pdfgen.plugins.configureLifecycleHooks
import no.nav.pdfgen.plugins.configureNais
import no.nav.pdfgen.plugins.configureReloadPDFGenCore
import no.nav.pdfgen.plugins.configureRouting
import no.nav.pdfgen.plugins.configureStatusPages
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.verapdf.gf.foundry.VeraGreenfieldFoundryProvider

val logger: Logger = LoggerFactory.getLogger("pdfgen")

fun main() {
    DefaultExports.initialize()

    val embeddedServer =
        embeddedServer(
            factory = Netty,
            module = Application::module,
            configure = {
                responseWriteTimeoutSeconds = 70
                connector { port = Environment().port }
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

    configureLifecycleHooks(applicationState = applicationState)
    configureContentNegotiation()
    configureStatusPages(templates = templates, environment = environment)
    configureNais(applicationState = applicationState)
    configureReloadPDFGenCore(environment = environment)
    configureRouting(environment = environment)
}

data class ApplicationState(
    var alive: Boolean = false,
    var ready: Boolean = false,
)
