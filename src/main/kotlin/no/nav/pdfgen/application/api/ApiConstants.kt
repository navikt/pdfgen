package no.nav.pdfgen.application.api

internal object ApiPaths {
    const val GENERATE_PDF = "/api/v1/genpdf"
    const val GENERATE_HTML = "/api/v1/genhtml"
    const val IS_ALIVE = "/internal/is_alive"
    const val IS_READY = "/internal/is_ready"
    const val PROMETHEUS = "/internal/prometheus"
}

internal const val APPLICATION_NAME_PARAMETER = "applicationName"
internal const val TEMPLATE_PARAMETER = "template"

internal object ApiMessages {
    const val TEMPLATE_OR_APPLICATION_NOT_FOUND = "Template or application not found"
}
