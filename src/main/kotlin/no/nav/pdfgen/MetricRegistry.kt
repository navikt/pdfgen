package no.nav.pdfgen

import io.prometheus.client.Summary

val HANDLEBARS_RENDERING_SUMMARY: Summary = Summary.Builder()
        .name("handlebars_rendering")
        .help("Time it takes for handlebars to render the template").register()
val JSOUP_PARSE_SUMMARY: Summary = Summary.Builder()
        .name("jsoup_parse")
        .help("Time it takes jsoup to parse the template").register()
