package no.nav.pdfgen

data class Environment(
    val port: Int = System.getenv("SERVER_PORT")?.toInt() ?: 8080,
    val isDevMode: Boolean = System.getenv("DEV_MODE")?.let { it == "true" } ?: false,
    val disablePdfGet: Boolean = System.getenv("DISABLE_PDF_GET")?.let { it == "true" } ?: false,
    val enableHtmlEndpoint: Boolean =
        System.getenv("ENABLE_HTML_ENDPOINT")?.let { it == "true" } ?: false,
)
