use axum::{
    body::Body,
    extract::{Path, State},
    http::{HeaderValue, StatusCode},
    response::{IntoResponse, Response},
    Json,
};
use serde_json::Value;
use tracing::{error, info};

use crate::{pdf as gen_pdf, template, AppState};

/// GET /api/v1/genpdf/{applicationName}/{template}
/// Renders a PDF from a template using pre-loaded test data.
pub async fn get_pdf(
    State(state): State<AppState>,
    Path((app_name, template_name)): Path<(String, String)>,
) -> Response {
    let data = {
        let data_map = state.data.read().await;
        data_map
            .get(&(app_name.clone(), template_name.clone()))
            .cloned()
    };

    match data {
        None => (
            StatusCode::NOT_FOUND,
            format!("Template or application not found: {}/{}", app_name, template_name),
        )
            .into_response(),
        Some(json_data) => {
            let html = {
                let hbs = state.hbs.read().await;
                let tmpl_name = format!("{}/{}", app_name, template_name);
                template::render_template(&hbs, &tmpl_name, &json_data)
            };
            match html {
                Err(e) => {
                    error!("Template rendering failed: {e}");
                    (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()).into_response()
                }
                Ok(html_str) => match gen_pdf::html_to_pdf(&html_str).await {
                    Err(e) => {
                        error!("PDF generation failed: {e}");
                        (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()).into_response()
                    }
                    Ok(pdf_bytes) => pdf_response(pdf_bytes),
                },
            }
        }
    }
}

/// POST /api/v1/genpdf/{applicationName}/{template}
/// Renders a PDF from a template with JSON data from the request body.
pub async fn post_pdf(
    State(state): State<AppState>,
    Path((app_name, template_name)): Path<(String, String)>,
    Json(json_data): Json<Value>,
) -> Response {
    let start = std::time::Instant::now();
    let tmpl_name = format!("{}/{}", app_name, template_name);
    let html = {
        let hbs = state.hbs.read().await;
        template::render_template(&hbs, &tmpl_name, &json_data)
    };
    match html {
        Err(e) => {
            error!("Template rendering failed for {tmpl_name}: {e}");
            not_found_response(&state, &format!("/api/v1/genpdf/{}/{}", app_name, template_name))
                .await
        }
        Ok(html_str) => match gen_pdf::html_to_pdf(&html_str).await {
            Err(e) => {
                error!("PDF generation failed: {e}");
                (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()).into_response()
            }
            Ok(pdf_bytes) => {
                info!(
                    "Done generating PDF in {}ms",
                    start.elapsed().as_millis()
                );
                pdf_response(pdf_bytes)
            }
        },
    }
}

/// POST /api/v1/genpdf/html/{applicationName}
/// Converts a raw HTML string to PDF.
pub async fn post_html_to_pdf(
    State(_state): State<AppState>,
    Path(_app_name): Path<String>,
    body: String,
) -> Response {
    match gen_pdf::html_to_pdf(&body).await {
        Err(e) => {
            error!("HTML-to-PDF conversion failed: {e}");
            (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()).into_response()
        }
        Ok(pdf_bytes) => pdf_response(pdf_bytes),
    }
}

/// POST /api/v1/genpdf/image/{applicationName}
/// Converts a JPEG or PNG image to PDF.
pub async fn post_image_to_pdf(
    State(_state): State<AppState>,
    Path(_app_name): Path<String>,
    request: axum::http::Request<Body>,
) -> Response {
    let content_type = request
        .headers()
        .get("content-type")
        .and_then(|v| v.to_str().ok())
        .unwrap_or("")
        .to_string();

    if !content_type.contains("jpeg") && !content_type.contains("png") && !content_type.contains("jpg") {
        return StatusCode::UNSUPPORTED_MEDIA_TYPE.into_response();
    }

    let body_bytes = match axum::body::to_bytes(request.into_body(), usize::MAX).await {
        Ok(b) => b,
        Err(e) => {
            return (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()).into_response();
        }
    };

    match gen_pdf::image_to_pdf(&body_bytes, &content_type).await {
        Err(e) => {
            error!("Image-to-PDF conversion failed: {e}");
            (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()).into_response()
        }
        Ok(pdf_bytes) => pdf_response(pdf_bytes),
    }
}

fn pdf_response(pdf_bytes: Vec<u8>) -> Response {
    let mut response = Response::new(Body::from(pdf_bytes));
    response.headers_mut().insert(
        "Content-Type",
        HeaderValue::from_static("application/pdf"),
    );
    response
}

pub async fn not_found_response(state: &AppState, path: &str) -> Response {
    let hbs = state.hbs.read().await;
    let template_names: Vec<String> = hbs
        .get_templates()
        .keys()
        .filter(|name| !name.contains("partials"))
        .map(|name| format!("/api/v1/genpdf/{}", name))
        .collect();
    let msg = format!(
        "Unknown path '{}'. Known templates:\n{}",
        path,
        template_names.join("\n")
    );
    (StatusCode::NOT_FOUND, msg).into_response()
}
