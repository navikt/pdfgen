package no.nav.pdfgen

import io.prometheus.client.Summary

val XMP_METADATA_SUMMARY: Summary = Summary.Builder().name("xmp_metadata")
        .help("The time it takes to create and serialize the xmp metadata").register()
val OUTPUT_INTENT_SUMMARY: Summary = Summary.Builder().name("output_intent")
        .help("The time it takes to read the color profile and add the output intent").register()
val DOCUMENT_CREATION_SUMMARY: Summary = Summary.Builder().name("document_creation")
        .help("Time it takes to call document.save()").register()
val HANDLEBARS_RENDERING_SUMMARY: Summary = Summary.Builder().name("handlebars_rendering")
        .help("Time it takes for handlebars to render the template").register()
val JSOUP_PARSE_SUMMARY: Summary = Summary.Builder().name("jsoup_parse")
        .help("Time it takes jsoup to parse the template").register()
