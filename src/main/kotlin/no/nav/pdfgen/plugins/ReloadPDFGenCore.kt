package no.nav.pdfgen.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import no.nav.pdfgen.Environment
import no.nav.pdfgen.core.PDFGenCore

fun Application.configureReloadPDFGenCore(environment: Environment) {
    install(
        createApplicationPlugin(name = "ReloadPDFGenCorePlugin") {
            onCall { _ -> if (environment.isDevMode) PDFGenCore.reloadEnvironment() }
        },
    )
}
