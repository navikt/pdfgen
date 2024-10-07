package no.nav.pdfgen.plugins

import io.ktor.server.application.*
import no.nav.pdfgen.Environment
import no.nav.pdfgen.core.PDFGenCore

fun Application.configureReloadPDFGenCorePlugin(environment: Environment) {
    install(
        createApplicationPlugin(name = "ReloadPDFGenCorePlugin") {
            onCall { _ -> if (environment.isDevMode) PDFGenCore.reloadEnvironment() }
        },
    )
}
