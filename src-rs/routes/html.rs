use axum::{
    extract::{Path, State},
    http::{HeaderValue, StatusCode},
    response::{IntoResponse, Response},
    Json,
};
use serde_json::Value;
use tracing::{error, info};

use crate::{template, AppState};

/// GET /api/v1/genhtml/{applicationName}/{template}
pub async fn get_html(
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
            format!(
                "Template or application not found: {}/{}",
                app_name, template_name
            ),
        )
            .into_response(),
        Some(json_data) => {
            let tmpl_name = format!("{}/{}", app_name, template_name);
            let html = {
                let hbs = state.hbs.read().await;
                template::render_template(&hbs, &tmpl_name, &json_data)
            };
            match html {
                Err(e) => {
                    error!("Template rendering failed: {e}");
                    (StatusCode::NOT_FOUND, "Template or application not found").into_response()
                }
                Ok(html_str) => html_response(html_str),
            }
        }
    }
}

/// POST /api/v1/genhtml/{applicationName}/{template}
pub async fn post_html(
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
            (StatusCode::NOT_FOUND, "Template or application not found").into_response()
        }
        Ok(html_str) => {
            info!(
                "Done generating HTML in {}ms",
                start.elapsed().as_millis()
            );
            html_response(html_str)
        }
    }
}

fn html_response(html: String) -> Response {
    let mut response = Response::new(html.into());
    response.headers_mut().insert(
        "Content-Type",
        HeaderValue::from_static("text/html; charset=utf-8"),
    );
    response
}
