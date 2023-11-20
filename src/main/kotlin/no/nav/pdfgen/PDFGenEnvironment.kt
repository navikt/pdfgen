package no.nav.pdfgen

data class PDFGenEnvironment(
    val disablePdfGet: Boolean = System.getenv("DISABLE_PDF_GET")?.let { it == "true" } ?: false,
    val enableHtmlEndpoint: Boolean =
        System.getenv("ENABLE_HTML_ENDPOINT")?.let { it == "true" } ?: false,
)
