package no.nav.pdfgen.application.metrics

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import java.util.concurrent.TimeUnit

internal object PdfRenderingMetric {
    const val NAME = "pdfgen_core.openhtmltopdf.rendering"
    const val APPLICATION_NAME_TAG = "application_name"
    const val ALL_APPLICATIONS = "all"
    const val TEMPLATE_TYPE_TAG = "template_type"
    const val TEMPLATE_TYPE_TEMPLATE = "template"
    const val TEMPLATE_TYPE_HTML_CONVERSION = "converthtml"
    const val TEMPLATE_TYPE_IMAGE_CONVERSION = "convertjpeg"
}

fun recordPdfRendering(
    meterRegistry: MeterRegistry,
    templateType: String,
    durationNanos: Long,
) {
    Timer
        .builder(PdfRenderingMetric.NAME)
        .description("Time it takes to render a PDF")
        .tag(PdfRenderingMetric.APPLICATION_NAME_TAG, PdfRenderingMetric.ALL_APPLICATIONS)
        .tag(PdfRenderingMetric.TEMPLATE_TYPE_TAG, templateType)
        .publishPercentileHistogram()
        .register(meterRegistry)
        .record(durationNanos, TimeUnit.NANOSECONDS)
}
