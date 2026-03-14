mod config;
mod metrics;
mod pdf;
mod routes;
mod state;
mod template;

use axum::{
    middleware,
    routing::{get, post},
    Router,
};
use handlebars::Handlebars;
use serde_json::Value;
use state::AppAliveness;
use std::{
    collections::HashMap,
    net::SocketAddr,
    sync::Arc,
};
use tokio::sync::RwLock;
use tracing::info;

#[derive(Clone)]
pub struct AppState {
    pub hbs: Arc<RwLock<Handlebars<'static>>>,
    pub images: Arc<HashMap<String, String>>,
    pub data: Arc<RwLock<HashMap<(String, String), Value>>>,
    pub aliveness: AppAliveness,
    pub config: config::Config,
}

#[tokio::main]
async fn main() {
    tracing_subscriber::fmt()
        .with_env_filter(
            tracing_subscriber::EnvFilter::try_from_default_env()
                .unwrap_or_else(|_| "pdfgen=info,tower_http=info".into()),
        )
        .init();

    let cfg = config::Config::default();

    info!("Loading templates from '{}'", cfg.templates_dir);
    let templates = template::load_templates_from_dir(&cfg.templates_dir)
        .unwrap_or_else(|e| {
            tracing::warn!("Failed to load templates: {e}");
            HashMap::new()
        });
    info!("Loaded {} templates", templates.len());

    info!("Loading images from '{}'", cfg.resources_dir);
    let images = Arc::new(template::load_images(&cfg.resources_dir));
    info!("Loaded {} images", images.len());

    info!("Loading test data from '{}'", cfg.data_dir);
    let data = template::load_test_data(&cfg.data_dir);
    info!("Loaded {} test data entries", data.len());

    let hbs = template::build_registry(&templates, images.clone())
        .expect("Failed to build Handlebars registry");

    let aliveness = AppAliveness::new();
    let aliveness_clone = aliveness.clone();

    metrics::register_metrics(prometheus::default_registry());

    let state = AppState {
        hbs: Arc::new(RwLock::new(hbs)),
        images,
        data: Arc::new(RwLock::new(data)),
        aliveness: aliveness.clone(),
        config: cfg.clone(),
    };

    let app = build_router(state, &cfg);

    let addr = SocketAddr::from(([0, 0, 0, 0], cfg.port));
    info!("Starting pdfgen server on {addr}");

    aliveness_clone.set_alive(true);
    aliveness_clone.set_ready(true);

    let listener = tokio::net::TcpListener::bind(addr)
        .await
        .expect("Failed to bind TCP listener");

    axum::serve(listener, app)
        .with_graceful_shutdown(shutdown_signal(aliveness_clone))
        .await
        .expect("Server error");
}

fn build_router(state: AppState, cfg: &config::Config) -> Router {
    let pdf_template_route = if !cfg.disable_pdf_get {
        axum::routing::get(routes::pdf::get_pdf).post(routes::pdf::post_pdf)
    } else {
        axum::routing::post(routes::pdf::post_pdf)
    };

    let pdf_router = Router::new()
        .route("/html/:app_name", post(routes::pdf::post_html_to_pdf))
        .route("/image/:app_name", post(routes::pdf::post_image_to_pdf))
        .route("/:app_name/:template", pdf_template_route);

    let mut html_router = Router::new();
    if cfg.enable_html_endpoint {
        let html_template_route = if !cfg.disable_pdf_get {
            axum::routing::get(routes::html::get_html).post(routes::html::post_html)
        } else {
            axum::routing::post(routes::html::post_html)
        };
        html_router = html_router.route("/:app_name/:template", html_template_route);
    }

    Router::new()
        .nest("/api/v1/genpdf", pdf_router)
        .nest("/api/v1/genhtml", html_router)
        .route("/internal/is_alive", get(routes::nais::is_alive))
        .route("/internal/is_ready", get(routes::nais::is_ready))
        .route("/internal/prometheus", get(routes::nais::prometheus_metrics))
        .layer(middleware::from_fn(http_metrics_middleware))
        .with_state(state)
}

async fn http_metrics_middleware(
    req: axum::extract::Request,
    next: axum::middleware::Next,
) -> axum::response::Response {
    let path = req.uri().path().to_string();
    let timer = metrics::HTTP_HISTOGRAM.with_label_values(&[&path]).start_timer();
    let resp = next.run(req).await;
    timer.observe_duration();
    resp
}

async fn shutdown_signal(aliveness: AppAliveness) {
    let ctrl_c = async {
        tokio::signal::ctrl_c()
            .await
            .expect("Failed to install Ctrl+C handler");
    };

    #[cfg(unix)]
    let terminate = async {
        tokio::signal::unix::signal(tokio::signal::unix::SignalKind::terminate())
            .expect("Failed to install SIGTERM handler")
            .recv()
            .await;
    };

    #[cfg(not(unix))]
    let terminate = std::future::pending::<()>();

    tokio::select! {
        _ = ctrl_c => {},
        _ = terminate => {},
    }

    info!("Shutdown signal received, stopping server...");
    aliveness.set_ready(false);
    aliveness.set_alive(false);
}
