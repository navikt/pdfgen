use once_cell::sync::Lazy;
use prometheus::{HistogramOpts, HistogramVec, Registry, TextEncoder};

pub static HTTP_HISTOGRAM: Lazy<HistogramVec> = Lazy::new(|| {
    HistogramVec::new(
        HistogramOpts::new(
            "requests_duration_seconds",
            "http requests durations for incoming requests in seconds",
        )
        .namespace("pdfgen"),
        &["path"],
    )
    .expect("Failed to create HTTP histogram")
});

pub static RENDERING_SUMMARY: Lazy<HistogramVec> = Lazy::new(|| {
    HistogramVec::new(
        HistogramOpts::new(
            "rendering_duration_seconds",
            "rendering durations for PDF generation in seconds",
        )
        .namespace("pdfgen"),
        &["application", "type"],
    )
    .expect("Failed to create rendering histogram")
});

pub fn register_metrics(registry: &Registry) {
    registry
        .register(Box::new(HTTP_HISTOGRAM.clone()))
        .expect("Failed to register HTTP histogram");
    registry
        .register(Box::new(RENDERING_SUMMARY.clone()))
        .expect("Failed to register rendering histogram");
}

pub fn gather_metrics(names: &[String]) -> anyhow::Result<String> {
    let registry = prometheus::default_registry();
    let metric_families = if names.is_empty() {
        registry.gather()
    } else {
        registry
            .gather()
            .into_iter()
            .filter(|mf| names.contains(&mf.get_name().to_string()))
            .collect()
    };

    let encoder = TextEncoder::new();
    let mut buf = String::new();
    encoder.encode_utf8(&metric_families, &mut buf)?;
    Ok(buf)
}
