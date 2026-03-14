use anyhow::{Context, Result};
use base64::{engine::general_purpose::STANDARD as BASE64, Engine};
use tokio::process::Command;

/// Convert HTML string to PDF bytes using headless Chrome.
pub async fn html_to_pdf(html: &str) -> Result<Vec<u8>> {
    let temp_dir = tempfile::TempDir::new().context("Failed to create temp dir")?;
    let input_path = temp_dir.path().join("input.html");
    let output_path = temp_dir.path().join("output.pdf");

    tokio::fs::write(&input_path, html)
        .await
        .context("Failed to write HTML to temp file")?;

    let chrome_bin = find_chrome_binary();
    let status = Command::new(&chrome_bin)
        .args([
            "--headless=new",
            "--no-sandbox",
            "--disable-gpu",
            "--disable-dev-shm-usage",
            "--disable-extensions",
            "--run-all-compositor-stages-before-draw",
            "--no-pdf-header-footer",
            &format!("--print-to-pdf={}", output_path.display()),
        ])
        .arg(input_path.to_str().unwrap())
        .stdout(std::process::Stdio::null())
        .stderr(std::process::Stdio::null())
        .status()
        .await
        .with_context(|| format!("Failed to run Chrome binary: {chrome_bin}"))?;

    if !status.success() {
        anyhow::bail!("Chrome exited with non-zero status: {}", status);
    }

    tokio::fs::read(&output_path)
        .await
        .context("Failed to read PDF output")
}

/// Wrap an image (JPEG/PNG) in an HTML page and convert to PDF.
pub async fn image_to_pdf(image_bytes: &[u8], content_type: &str) -> Result<Vec<u8>> {
    let mime = if content_type.contains("png") {
        "image/png"
    } else {
        "image/jpeg"
    };
    let b64 = BASE64.encode(image_bytes);
    let html = format!(
        r#"<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8"/>
<style>
body {{ margin: 0; padding: 0; }}
img {{ max-width: 100%; display: block; }}
</style>
</head>
<body>
<img src="data:{mime};base64,{b64}" alt="image"/>
</body>
</html>"#
    );
    html_to_pdf(&html).await
}

fn find_chrome_binary() -> String {
    let candidates = [
        std::env::var("CHROME_BINARY").ok(),
        Some("google-chrome".to_string()),
        Some("chromium".to_string()),
        Some("chromium-browser".to_string()),
        Some("/usr/bin/google-chrome".to_string()),
        Some("/usr/bin/chromium".to_string()),
    ];
    for candidate in candidates.into_iter().flatten() {
        if std::path::Path::new(&candidate).exists()
            || which_exists(&candidate)
        {
            return candidate;
        }
    }
    "google-chrome".to_string()
}

fn which_exists(name: &str) -> bool {
    std::process::Command::new("which")
        .arg(name)
        .output()
        .map(|o| o.status.success())
        .unwrap_or(false)
}
