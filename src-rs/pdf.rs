use anyhow::{Context, Result};
use std::collections::HashMap;
use std::path::Path;
use typst::foundations::Bytes;

use crate::typst_world;

/// Convert HTML string to PDF bytes using Typst.
///
/// The HTML content is passed to a minimal Typst document as a raw virtual
/// file and rendered. For best results, templates should use native Typst
/// (`.typ`) format and `typst_to_pdf` directly.
pub fn html_to_pdf(html: &str, fonts_dir: &str, root: &Path) -> Result<Vec<u8>> {
    // Build a Typst document that displays the HTML content as a raw block
    // This allows PDF generation without an external browser.
    let typst_source = r#"#set document(title: "pdfgen")
#set page(margin: (top: 1cm, bottom: 1cm, left: 1cm, right: 1cm))
#let content = read("/html-content", encoding: none)
#raw(str(content), lang: "html")
"#
    .to_string();

    let mut vfiles = HashMap::new();
    vfiles.insert("/html-content".to_string(), Bytes::new(html.as_bytes().to_vec()));

    typst_world::compile_to_pdf(fonts_dir, root, "/main.typ", typst_source, vfiles)
}

/// Render a Typst template to PDF bytes with JSON data injected as data.json.
///
/// The template can access the data via `#let data = json("data.json")`.
#[allow(dead_code)]
pub fn typst_to_pdf(
    template_source: &str,
    json_data: &serde_json::Value,
    fonts_dir: &str,
    root: &Path,
) -> Result<Vec<u8>> {
    let json_bytes = serde_json::to_vec(json_data).context("Failed to serialize JSON data")?;
    let mut vfiles = HashMap::new();
    vfiles.insert("/data.json".to_string(), Bytes::new(json_bytes));

    typst_world::compile_to_pdf(
        fonts_dir,
        root,
        "/main.typ",
        template_source.to_string(),
        vfiles,
    )
}

/// Wrap an image (JPEG/PNG) in a Typst document and convert to PDF.
pub fn image_to_pdf(
    image_bytes: &[u8],
    content_type: &str,
    fonts_dir: &str,
    root: &Path,
) -> Result<Vec<u8>> {
    let fmt = if content_type.contains("png") { "png" } else { "jpg" };
    let typst_source = format!(
        r#"#set page(margin: 0pt, width: auto, height: auto)
#let img-data = read("/image-data", encoding: none)
#image.decode(img-data, format: "{fmt}")
"#
    );

    let mut vfiles = HashMap::new();
    vfiles.insert("/image-data".to_string(), Bytes::new(image_bytes.to_vec()));

    typst_world::compile_to_pdf(fonts_dir, root, "/main.typ", typst_source, vfiles)
}
