use axum::{
    extract::{Query, State},
    http::StatusCode,
    response::{IntoResponse, Response},
};
use std::collections::HashMap;

use crate::{metrics, AppState};

pub async fn is_alive(State(state): State<AppState>) -> Response {
    if state.aliveness.is_alive() {
        (StatusCode::OK, "I'm alive").into_response()
    } else {
        (StatusCode::INTERNAL_SERVER_ERROR, "I'm dead x_x").into_response()
    }
}

pub async fn is_ready(State(state): State<AppState>) -> Response {
    if state.aliveness.is_ready() {
        (StatusCode::OK, "I'm ready").into_response()
    } else {
        (
            StatusCode::INTERNAL_SERVER_ERROR,
            "Please wait! I'm not ready :(",
        )
            .into_response()
    }
}

pub async fn prometheus_metrics(
    Query(params): Query<HashMap<String, String>>,
) -> Response {
    let names: Vec<String> = params
        .iter()
        .filter(|(k, _)| k.as_str() == "name[]")
        .map(|(_, v)| v.clone())
        .collect();

    match metrics::gather_metrics(&names) {
        Ok(output) => {
            let mut resp = output.into_response();
            resp.headers_mut().insert(
                "Content-Type",
                axum::http::HeaderValue::from_static(
                    "text/plain; version=0.0.4; charset=utf-8",
                ),
            );
            resp
        }
        Err(e) => (
            StatusCode::INTERNAL_SERVER_ERROR,
            format!("Failed to gather metrics: {e}"),
        )
            .into_response(),
    }
}
