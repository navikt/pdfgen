package no.nav.pdfgen

import com.openhtmltopdf.slf4j.Slf4jLogger
import com.openhtmltopdf.util.XRLog
import io.ktor.server.application.Application
import io.ktor.server.netty.EngineMain
import no.nav.pdfgen.application.api.isReadyKey
import no.nav.pdfgen.application.metrics.configureMonitoring
import no.nav.pdfgen.application.metrics.createPrometheusMeterRegistry
import no.nav.pdfgen.core.PDFGenCore
import no.nav.pdfgen.plugins.configureContentNegotiation
import no.nav.pdfgen.plugins.configureHealthAndMetrics
import no.nav.pdfgen.plugins.configureLifecycleHooks
import no.nav.pdfgen.plugins.configureReloadPDFGenCore
import no.nav.pdfgen.plugins.configureRouting
import no.nav.pdfgen.plugins.configureStatusPages
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.verapdf.gf.foundry.VeraGreenfieldFoundryProvider
import no.nav.pdfgen.core.Environment as PDFGenCoreEnvironment

val logger: Logger = LoggerFactory.getLogger("pdfgen")

fun main(args: Array<String>) = EngineMain.main(args)

fun Application.module() {
    val config = environment.config

    val pdfgenEnvironment = Environment(
        isDevMode = config.propertyOrNull("pdfgen.isDevMode")?.getString() == "true",
        disablePdfGet = config.propertyOrNull("pdfgen.disablePdfGet")?.getString() == "true",
        enableHtmlEndpoint = config.propertyOrNull("pdfgen.enableHtmlEndpoint")?.getString() == "true",
    )

    // Route OpenHTMLToPDF logs through the application's SLF4J/Logback configuration.
    XRLog.setLoggerImpl(Slf4jLogger())
    VeraGreenfieldFoundryProvider.initialise()

    val meterRegistry = createPrometheusMeterRegistry()
    val coreEnvironment = PDFGenCoreEnvironment()
    PDFGenCore.init(
        initialEnvironment = coreEnvironment,
        meterRegistry = meterRegistry,
    )

    val templates = coreEnvironment.templates

    configureLifecycleHooks()
    configureMonitoring(meterRegistry = meterRegistry)
    configureContentNegotiation()
    configureStatusPages(
        templates = templates,
        environment = pdfgenEnvironment,
    )
    configureReloadPDFGenCore(environment = pdfgenEnvironment)
    configureHealthAndMetrics(meterRegistry = meterRegistry)
    configureRouting(
        environment = pdfgenEnvironment,
        meterRegistry = meterRegistry,
    )

    // application is now ready for incoming requests
    attributes.put(isReadyKey, true)
}
