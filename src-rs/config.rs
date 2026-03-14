use std::env;

#[derive(Clone, Debug)]
pub struct Config {
    pub port: u16,
    pub dev_mode: bool,
    pub disable_pdf_get: bool,
    pub enable_html_endpoint: bool,
    pub templates_dir: String,
    pub resources_dir: String,
    pub fonts_dir: String,
    pub data_dir: String,
}

impl Default for Config {
    fn default() -> Self {
        Self {
            port: env::var("SERVER_PORT")
                .ok()
                .and_then(|v| v.parse().ok())
                .unwrap_or(8080),
            dev_mode: env::var("DEV_MODE").map(|v| v == "true").unwrap_or(false),
            disable_pdf_get: env::var("DISABLE_PDF_GET")
                .map(|v| v == "true")
                .unwrap_or(false),
            enable_html_endpoint: env::var("ENABLE_HTML_ENDPOINT")
                .map(|v| v == "true")
                .unwrap_or(false),
            templates_dir: env::var("TEMPLATES_DIR").unwrap_or_else(|_| "templates".to_string()),
            resources_dir: env::var("RESOURCES_DIR").unwrap_or_else(|_| "resources".to_string()),
            fonts_dir: env::var("FONTS_DIR").unwrap_or_else(|_| "fonts".to_string()),
            data_dir: env::var("DATA_DIR").unwrap_or_else(|_| "data".to_string()),
        }
    }
}
