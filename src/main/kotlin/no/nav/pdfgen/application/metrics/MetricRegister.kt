package no.nav.pdfgen.application.metrics

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import java.util.concurrent.TimeUnit

fun recordPdfRendering(
    meterRegistry: MeterRegistry,
    templateType: String,
    durationNanos: Long,
) {
    Timer
        .builder("pdfgen_core.openhtmltopdf.rendering")
        .description("Time it takes to render a PDF")
        .tag("application_name", "all")
        .tag("template_type", templateType)
        .publishPercentileHistogram()
        .register(meterRegistry)
        .record(durationNanos, TimeUnit.NANOSECONDS)
}
