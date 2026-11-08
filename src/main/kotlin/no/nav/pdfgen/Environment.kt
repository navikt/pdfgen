package no.nav.pdfgen

data class Environment(
    val isDevMode: Boolean = false,
    val disablePdfGet: Boolean = false,
    val enableHtmlEndpoint: Boolean = false,
)
