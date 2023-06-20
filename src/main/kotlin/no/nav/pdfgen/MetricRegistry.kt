package no.nav.pdfgen

import io.prometheus.client.Summary

val HANDLEBARS_RENDERING_SUMMARY: Summary =
    Summary.Builder()
        .name("handlebars_rendering")
        .help("Time it takes for handlebars to render the template")
        .register()
val OPENHTMLTOPDF_RENDERING_SUMMARY: Summary =
    Summary.Builder()
        .name("openhtmltopdf_rendering_summary")
        .help("Time it takes to render a PDF")
        .labelNames("application_name", "template_type")
        .register()
val JSOUP_PARSE_SUMMARY: Summary =
    Summary.Builder()
        .name("jsoup_parse")
        .help("Time it takes jsoup to parse the template")
        .register()
