use anyhow::{Context, Result};
use base64::{engine::general_purpose::STANDARD as BASE64, Engine};
use chrono::{Datelike, NaiveDate};
use handlebars::{
    BlockContext, Context as HbsContext, Handlebars, Helper, HelperDef, HelperResult, Output,
    RenderContext, RenderError, RenderErrorReason, Renderable,
};
use serde_json::Value;
use std::collections::HashMap;
use std::path::Path;
use std::sync::Arc;
use walkdir::WalkDir;

/// Loads all .hbs templates from the templates directory recursively.
pub fn load_templates_from_dir(templates_dir: &str) -> Result<HashMap<String, String>> {
    let mut templates = HashMap::new();
    let base = Path::new(templates_dir);

    for entry in WalkDir::new(templates_dir)
        .follow_links(true)
        .into_iter()
        .filter_map(|e| e.ok())
    {
        let path = entry.path();
        if path.is_file() {
            if let Some(ext) = path.extension() {
                if ext == "hbs" {
                    let relative = path
                        .strip_prefix(base)
                        .context("Failed to strip prefix")?;
                    let name = relative
                        .with_extension("")
                        .to_string_lossy()
                        .replace('\\', "/");
                    let source = std::fs::read_to_string(path)
                        .context("Failed to read template file")?;
                    templates.insert(name, source);
                }
            }
        }
    }
    Ok(templates)
}

/// Loads all image files from the resources directory as base64 data URLs.
pub fn load_images(resources_dir: &str) -> HashMap<String, String> {
    let mut images = HashMap::new();
    let valid_extensions = ["jpg", "jpeg", "png", "bmp", "svg"];

    for entry in WalkDir::new(resources_dir)
        .max_depth(1)
        .into_iter()
        .filter_map(|e| e.ok())
    {
        let path = entry.path();
        if path.is_file() {
            if let Some(ext) = path.extension().and_then(|e| e.to_str()) {
                let ext_lower = ext.to_lowercase();
                if valid_extensions.contains(&ext_lower.as_str()) {
                    if let Ok(bytes) = std::fs::read(path) {
                        let mime_ext = match ext_lower.as_str() {
                            "jpg" | "jpeg" => "jpeg",
                            "svg" => "svg+xml",
                            other => other,
                        };
                        let b64 = BASE64.encode(&bytes);
                        let data_url = format!("data:image/{mime_ext};base64,{b64}");
                        if let Some(filename) = path.file_name().and_then(|n| n.to_str()) {
                            images.insert(filename.to_string(), data_url);
                        }
                    }
                }
            }
        }
    }
    images
}

/// Loads all test data JSON files from the data directory.
pub fn load_test_data(data_dir: &str) -> HashMap<(String, String), Value> {
    let mut data = HashMap::new();
    let base = Path::new(data_dir);

    for entry in WalkDir::new(data_dir)
        .min_depth(2)
        .max_depth(2)
        .into_iter()
        .filter_map(|e| e.ok())
    {
        let path = entry.path();
        if path.is_file() {
            if let Some(ext) = path.extension() {
                if ext == "json" {
                    if let (Ok(relative), Ok(content)) =
                        (path.strip_prefix(base), std::fs::read_to_string(path))
                    {
                        if let Ok(value) = serde_json::from_str::<Value>(&content) {
                            let parts: Vec<&str> = relative
                                .components()
                                .map(|c| c.as_os_str().to_str().unwrap_or(""))
                                .collect();
                            if parts.len() == 2 {
                                let app = parts[0].to_string();
                                let template = Path::new(parts[1])
                                    .with_extension("")
                                    .to_string_lossy()
                                    .to_string();
                                data.insert((app, template), value);
                            }
                        }
                    }
                }
            }
        }
    }
    data
}

/// Build a Handlebars registry with all templates and helpers registered.
pub fn build_registry(
    templates: &HashMap<String, String>,
    images: Arc<HashMap<String, String>>,
) -> Result<Handlebars<'static>> {
    let mut hbs = Handlebars::new();
    hbs.set_strict_mode(false);

    for (name, source) in templates {
        hbs.register_template_string(name, source)
            .with_context(|| format!("Failed to register template: {name}"))?;
    }

    register_helpers(&mut hbs, images);
    Ok(hbs)
}

fn register_helpers(hbs: &mut Handlebars<'static>, images: Arc<HashMap<String, String>>) {
    hbs.register_helper("iso_to_nor_date", Box::new(IsoToNorDateHelper));
    hbs.register_helper("iso_to_date", Box::new(IsoToDateHelper));
    hbs.register_helper("iso_to_nor_datetime", Box::new(IsoToNorDatetimeHelper));
    hbs.register_helper("iso_to_nor_datetime_seconds", Box::new(IsoToNorDatetimeSecondsHelper));
    hbs.register_helper("iso_to_year_month", Box::new(IsoToYearMonthHelper));
    hbs.register_helper("iso_to_long_date", Box::new(IsoToLongDateHelper));
    hbs.register_helper("duration", Box::new(DurationHelper));
    hbs.register_helper("json_to_period", Box::new(JsonToPeriodHelper));
    hbs.register_helper("insert_at", Box::new(InsertAtHelper));
    hbs.register_helper("eq", Box::new(EqHelper));
    hbs.register_helper("not_eq", Box::new(NotEqHelper));
    hbs.register_helper("gt", Box::new(GtHelper));
    hbs.register_helper("lt", Box::new(LtHelper));
    hbs.register_helper("safe", Box::new(SafeHelper));
    hbs.register_helper("image", Box::new(ImageHelper { images }));
    hbs.register_helper("capitalize", Box::new(CapitalizeHelper));
    hbs.register_helper("capitalize_names", Box::new(CapitalizeNamesHelper));
    hbs.register_helper("uppercase", Box::new(UppercaseHelper));
    hbs.register_helper("lowercase", Box::new(LowercaseHelper));
    hbs.register_helper("inc", Box::new(IncHelper));
    hbs.register_helper("formatComma", Box::new(FormatCommaHelper));
    hbs.register_helper("format_money", Box::new(FormatMoneyHelper));
    hbs.register_helper("num_to_nor", Box::new(NumToNorHelper));
    hbs.register_helper("any", Box::new(AnyHelper));
    hbs.register_helper("contains_field", Box::new(ContainsFieldHelper));
    hbs.register_helper("contains_all", Box::new(ContainsAllHelper));
    hbs.register_helper("currency_no", Box::new(CurrencyNoHelper));
    hbs.register_helper("int_as_currency_no", Box::new(IntAsCurrencyNoHelper));
    hbs.register_helper("string_as_currency_no", Box::new(StringAsCurrencyNoHelper));
    hbs.register_helper("is_defined", Box::new(IsDefinedHelper));
    hbs.register_helper("breaklines", Box::new(BreakLinesHelper));
    hbs.register_helper("filter", Box::new(FilterHelper));
    hbs.register_helper("concat", Box::new(ConcatHelper));
    hbs.register_helper("stringify", Box::new(StringifyHelper));
    hbs.register_helper("now", Box::new(NowHelper));
    hbs.register_helper("now_date", Box::new(NowDateHelper));
    hbs.register_helper("block", Box::new(BlockHelper));
}

fn value_to_string(val: &Value) -> String {
    match val {
        Value::String(s) => s.clone(),
        Value::Number(n) => n.to_string(),
        Value::Bool(b) => b.to_string(),
        Value::Null => String::new(),
        _ => val.to_string(),
    }
}

fn is_falsy(val: &Value) -> bool {
    match val {
        Value::Null => true,
        Value::Bool(false) => true,
        Value::String(s) => s.is_empty(),
        Value::Array(a) => a.is_empty(),
        Value::Number(n) => n.as_f64().map(|f| f == 0.0).unwrap_or(false),
        _ => false,
    }
}

fn format_date_to_nor(date_str: &str) -> String {
    let s = date_str.trim();
    if let Ok(dt) = chrono::DateTime::parse_from_rfc3339(s) {
        return dt.format("%d.%m.%Y").to_string();
    }
    if let Ok(dt) = chrono::NaiveDateTime::parse_from_str(s, "%Y-%m-%dT%H:%M:%S") {
        return dt.format("%d.%m.%Y").to_string();
    }
    if let Ok(dt) = chrono::NaiveDateTime::parse_from_str(s, "%Y-%m-%dT%H:%M") {
        return dt.format("%d.%m.%Y").to_string();
    }
    if s.len() == 7 {
        if let Ok(d) = NaiveDate::parse_from_str(&format!("{}-01", s), "%Y-%m-%d") {
            return d.format("%m.%Y").to_string();
        }
    }
    if let Ok(d) = NaiveDate::parse_from_str(s, "%Y-%m-%d") {
        return d.format("%d.%m.%Y").to_string();
    }
    if let Ok(d) = NaiveDate::parse_from_str(&format!("{}-01", s), "%Y-%m-%d") {
        return d.format("%d.%m.%Y").to_string();
    }
    s.to_string()
}

fn format_datetime_to_nor(s: &str) -> String {
    let s = s.trim();
    if let Ok(dt) = chrono::DateTime::parse_from_rfc3339(s) {
        return dt.format("%d.%m.%Y %H:%M").to_string();
    }
    if let Ok(dt) = chrono::NaiveDateTime::parse_from_str(s, "%Y-%m-%dT%H:%M:%S") {
        return dt.format("%d.%m.%Y %H:%M").to_string();
    }
    if let Ok(dt) = chrono::NaiveDateTime::parse_from_str(s, "%Y-%m-%dT%H:%M") {
        return dt.format("%d.%m.%Y %H:%M").to_string();
    }
    s.to_string()
}

fn format_datetime_seconds_to_nor(s: &str) -> String {
    let s = s.trim();
    if let Ok(dt) = chrono::DateTime::parse_from_rfc3339(s) {
        return dt.format("%d.%m.%Y %H:%M:%S").to_string();
    }
    if let Ok(dt) = chrono::NaiveDateTime::parse_from_str(s, "%Y-%m-%dT%H:%M:%S") {
        return dt.format("%d.%m.%Y %H:%M:%S").to_string();
    }
    s.to_string()
}

fn nor_month_name(month: u32) -> &'static str {
    match month {
        1 => "januar", 2 => "februar", 3 => "mars", 4 => "april",
        5 => "mai", 6 => "juni", 7 => "juli", 8 => "august",
        9 => "september", 10 => "oktober", 11 => "november", 12 => "desember",
        _ => "ukjent",
    }
}

fn format_long_date(s: &str) -> String {
    let s = s.trim();
    let d = if let Ok(dt) = chrono::NaiveDateTime::parse_from_str(s, "%Y-%m-%dT%H:%M:%S") {
        dt.date()
    } else if let Ok(d) = NaiveDate::parse_from_str(s, "%Y-%m-%d") {
        d
    } else {
        return s.to_string();
    };
    format!("{}. {} {}", d.day(), nor_month_name(d.month()), d.year())
}

fn format_currency_no(s: &str, without_decimals: bool) -> String {
    let parts: Vec<&str> = s.split('.').collect();
    let integer_part = parts[0];
    let formatted: String = integer_part
        .chars()
        .rev()
        .collect::<Vec<char>>()
        .chunks(3)
        .map(|c| c.iter().collect::<String>())
        .collect::<Vec<String>>()
        .join("\u{00A0}")
        .chars()
        .rev()
        .collect();

    if without_decimals {
        formatted
    } else {
        let decimals = parts
            .get(1)
            .map(|d| {
                let padded = format!("{:0<2}", d);
                padded[..padded.len().min(2)].to_string()
            })
            .unwrap_or_else(|| "00".to_string());
        format!("{},{}", formatted, decimals)
    }
}

fn capitalize_words(s: &str, splitter: char) -> String {
    s.split(splitter)
        .map(|word| {
            let trimmed = word.trim();
            if trimmed.is_empty() {
                trimmed.to_string()
            } else {
                let mut chars = trimmed.chars();
                match chars.next() {
                    None => String::new(),
                    Some(first) => first.to_uppercase().to_string() + chars.as_str(),
                }
            }
        })
        .collect::<Vec<_>>()
        .join(&splitter.to_string())
}

fn get_nested_value(val: &Value, path: &str) -> Option<String> {
    let parts: Vec<&str> = path.split('.').collect();
    let mut current = val;
    for part in &parts {
        current = current.get(part)?;
    }
    Some(value_to_string(current))
}

// ---- Helper macro for simple output-only helpers ----
macro_rules! simple_helper {
    ($name:ident, $body:expr) => {
        struct $name;
        impl HelperDef for $name {
            fn call<'reg: 'rc, 'rc>(
                &self,
                h: &Helper<'rc>,
                _r: &'reg Handlebars<'reg>,
                _ctx: &'rc HbsContext,
                _rc: &mut RenderContext<'reg, 'rc>,
                out: &mut dyn Output,
            ) -> HelperResult {
                #[allow(clippy::redundant_closure_call)]
                $body(h, out)
            }
        }
    };
}

simple_helper!(IsoToNorDateHelper, |h: &Helper, out: &mut dyn Output| {
    if let Some(p) = h.param(0) {
        let s = value_to_string(p.value());
        if !s.is_empty() {
            out.write(&format_date_to_nor(&s))?;
        }
    }
    Ok(())
});

simple_helper!(IsoToDateHelper, |h: &Helper, out: &mut dyn Output| {
    if let Some(p) = h.param(0) {
        let s = value_to_string(p.value());
        if s.is_empty() { return Ok(()); }
        let formatted = if s.len() == 7 {
            if let Ok(d) = NaiveDate::parse_from_str(&format!("{}-01", s), "%Y-%m-%d") {
                d.format("%m.%Y").to_string()
            } else { s.clone() }
        } else if let Ok(d) = NaiveDate::parse_from_str(&s, "%Y-%m-%d") {
            d.format("%d.%m.%Y").to_string()
        } else {
            format_date_to_nor(&s)
        };
        out.write(&formatted)?;
    }
    Ok(())
});

simple_helper!(IsoToNorDatetimeHelper, |h: &Helper, out: &mut dyn Output| {
    if let Some(p) = h.param(0) {
        let s = value_to_string(p.value());
        if !s.is_empty() { out.write(&format_datetime_to_nor(&s))?; }
    }
    Ok(())
});

simple_helper!(IsoToNorDatetimeSecondsHelper, |h: &Helper, out: &mut dyn Output| {
    if let Some(p) = h.param(0) {
        let s = value_to_string(p.value());
        if !s.is_empty() { out.write(&format_datetime_seconds_to_nor(&s))?; }
    }
    Ok(())
});

simple_helper!(IsoToYearMonthHelper, |h: &Helper, out: &mut dyn Output| {
    if let Some(p) = h.param(0) {
        let s = value_to_string(p.value());
        if s.is_empty() { return Ok(()); }
        let formatted = if let Ok(d) = NaiveDate::parse_from_str(&s, "%Y-%m-%d") {
            d.format("%m.%Y").to_string()
        } else if s.len() == 7 {
            if let Ok(d) = NaiveDate::parse_from_str(&format!("{}-01", s), "%Y-%m-%d") {
                d.format("%m.%Y").to_string()
            } else { s.clone() }
        } else { s.clone() };
        out.write(&formatted)?;
    }
    Ok(())
});

simple_helper!(IsoToLongDateHelper, |h: &Helper, out: &mut dyn Output| {
    if let Some(p) = h.param(0) {
        let s = value_to_string(p.value());
        if !s.is_empty() { out.write(&format_long_date(&s))?; }
    }
    Ok(())
});

simple_helper!(DurationHelper, |h: &Helper, out: &mut dyn Output| {
    let from_s = h.param(0).map(|v| value_to_string(v.value())).unwrap_or_default();
    let to_s = h.param(1).map(|v| value_to_string(v.value())).unwrap_or_default();
    if let (Ok(from), Ok(to)) = (
        NaiveDate::parse_from_str(&from_s, "%Y-%m-%d"),
        NaiveDate::parse_from_str(&to_s, "%Y-%m-%d"),
    ) {
        out.write(&(to - from).num_days().to_string())?;
    }
    Ok(())
});

simple_helper!(JsonToPeriodHelper, |h: &Helper, out: &mut dyn Output| {
    if let Some(p) = h.param(0) {
        let val = p.value();
        let json_val = match val {
            Value::String(s) => serde_json::from_str::<Value>(s).unwrap_or_else(|_| val.clone()),
            _ => val.clone(),
        };
        let fom = json_val.get("fom").and_then(|v| v.as_str());
        let tom = json_val.get("tom").and_then(|v| v.as_str())
            .or_else(|| json_val.get("til").and_then(|v| v.as_str()));
        if let Some(fom_str) = fom {
            let fom_f = format_date_to_nor(fom_str);
            if let Some(tom_str) = tom {
                out.write(&format!("{} - {}", fom_f, format_date_to_nor(tom_str)))?;
            } else {
                out.write(&fom_f)?;
            }
        }
    }
    Ok(())
});

simple_helper!(InsertAtHelper, |h: &Helper, out: &mut dyn Output| {
    if let Some(p) = h.param(0) {
        let mut s = value_to_string(p.value());
        let divider = h.hash_get("divider")
            .and_then(|v| v.value().as_str())
            .unwrap_or(" ");
        let positions: Vec<usize> = h.params().iter().skip(1)
            .filter_map(|p| p.value().as_u64().map(|n| n as usize))
            .collect();
        let mut offset = 0;
        for pos in positions {
            let insert_pos = (pos + offset).min(s.len());
            s.insert_str(insert_pos, divider);
            offset += divider.len();
        }
        out.write(&s)?;
    }
    Ok(())
});

simple_helper!(SafeHelper, |h: &Helper, out: &mut dyn Output| {
    if let Some(p) = h.param(0) {
        out.write(&value_to_string(p.value()))?;
    }
    Ok(())
});

simple_helper!(CapitalizeHelper, |h: &Helper, out: &mut dyn Output| {
    if let Some(p) = h.param(0) {
        let s = value_to_string(p.value()).to_lowercase();
        let capitalized = {
            let mut chars = s.chars();
            match chars.next() {
                None => String::new(),
                Some(first) => first.to_uppercase().to_string() + chars.as_str(),
            }
        };
        out.write(&capitalized)?;
    }
    Ok(())
});

simple_helper!(CapitalizeNamesHelper, |h: &Helper, out: &mut dyn Output| {
    if let Some(p) = h.param(0) {
        let s = value_to_string(p.value());
        let normalized = s.trim().split_whitespace().collect::<Vec<_>>().join(" ").to_lowercase();
        let result = capitalize_words(&capitalize_words(&capitalize_words(&normalized, ' '), '-'), '\'');
        out.write(&result)?;
    }
    Ok(())
});

simple_helper!(UppercaseHelper, |h: &Helper, out: &mut dyn Output| {
    if let Some(p) = h.param(0) { out.write(&value_to_string(p.value()).to_uppercase())?; }
    Ok(())
});

simple_helper!(LowercaseHelper, |h: &Helper, out: &mut dyn Output| {
    if let Some(p) = h.param(0) { out.write(&value_to_string(p.value()).to_lowercase())?; }
    Ok(())
});

simple_helper!(IncHelper, |h: &Helper, out: &mut dyn Output| {
    if let Some(p) = h.param(0) {
        if let Some(n) = p.value().as_i64() { out.write(&(n + 1).to_string())?; }
    }
    Ok(())
});

simple_helper!(FormatCommaHelper, |h: &Helper, out: &mut dyn Output| {
    if let Some(p) = h.param(0) { out.write(&value_to_string(p.value()).replace('.', ","))?; }
    Ok(())
});

simple_helper!(NumToNorHelper, |h: &Helper, out: &mut dyn Output| {
    if let Some(p) = h.param(0) { out.write(&value_to_string(p.value()).replace('.', ","))?; }
    Ok(())
});

simple_helper!(FormatMoneyHelper, |h: &Helper, out: &mut dyn Output| {
    if let Some(p) = h.param(0) {
        out.write(&format_currency_no(&value_to_string(p.value()), false))?;
    }
    Ok(())
});

simple_helper!(CurrencyNoHelper, |h: &Helper, out: &mut dyn Output| {
    if let Some(p) = h.param(0) {
        let without_decimals = h.param(1).and_then(|v| v.value().as_bool()).unwrap_or(false);
        out.write(&format_currency_no(&value_to_string(p.value()), without_decimals))?;
    }
    Ok(())
});

simple_helper!(IntAsCurrencyNoHelper, |h: &Helper, out: &mut dyn Output| {
    if let Some(p) = h.param(0) {
        if let Some(n) = p.value().as_i64() {
            let kr = n / 100;
            let ore = (n % 100).abs();
            let formatted_kr: String = kr.to_string().chars().rev()
                .collect::<Vec<char>>().chunks(3)
                .map(|c| c.iter().collect::<String>())
                .collect::<Vec<_>>().join("\u{00A0}")
                .chars().rev().collect();
            out.write(&format!("{},{:02}", formatted_kr, ore))?;
        }
    }
    Ok(())
});

simple_helper!(StringAsCurrencyNoHelper, |h: &Helper, out: &mut dyn Output| {
    if let Some(p) = h.param(0) {
        let digits: String = value_to_string(p.value()).chars().filter(|c| c.is_ascii_digit()).collect();
        if let Ok(n) = digits.parse::<i64>() {
            let kr = n / 100;
            let ore = n % 100;
            let formatted_kr: String = kr.to_string().chars().rev()
                .collect::<Vec<char>>().chunks(3)
                .map(|c| c.iter().collect::<String>())
                .collect::<Vec<_>>().join("\u{00A0}")
                .chars().rev().collect();
            out.write(&format!("{},{:02}", formatted_kr, ore))?;
        }
    }
    Ok(())
});

simple_helper!(BreakLinesHelper, |h: &Helper, out: &mut dyn Output| {
    if let Some(p) = h.param(0) {
        let s = value_to_string(p.value());
        let escaped = handlebars::html_escape(&s);
        let with_br = escaped
            .replace("\\r\\n", "<br/>").replace("\\n", "<br/>")
            .replace("\r\n", "<br/>").replace('\n', "<br/>");
        out.write(&with_br)?;
    }
    Ok(())
});

simple_helper!(ConcatHelper, |h: &Helper, out: &mut dyn Output| {
    let parts: Vec<String> = h.params().iter().map(|p| value_to_string(p.value())).collect();
    out.write(&parts.join(" "))?;
    Ok(())
});

simple_helper!(StringifyHelper, |h: &Helper, out: &mut dyn Output| {
    if let Some(p) = h.param(0) {
        if let Ok(s) = serde_json::to_string(p.value()) { out.write(&s)?; }
    }
    Ok(())
});

simple_helper!(NowHelper, |_h: &Helper, out: &mut dyn Output| {
    out.write(&chrono::Local::now().naive_local().format("%Y-%m-%dT%H:%M:%S").to_string())?;
    Ok(())
});

simple_helper!(NowDateHelper, |_h: &Helper, out: &mut dyn Output| {
    out.write(&chrono::Local::now().date_naive().format("%Y-%m-%d").to_string())?;
    Ok(())
});

// ---- Block helpers (need r and ctx with proper lifetimes) ----

struct ImageHelper {
    images: Arc<HashMap<String, String>>,
}
impl HelperDef for ImageHelper {
    fn call<'reg: 'rc, 'rc>(
        &self,
        h: &Helper<'rc>,
        _r: &'reg Handlebars<'reg>,
        _ctx: &'rc HbsContext,
        _rc: &mut RenderContext<'reg, 'rc>,
        out: &mut dyn Output,
    ) -> HelperResult {
        if let Some(p) = h.param(0) {
            let name = value_to_string(p.value());
            if let Some(data_url) = self.images.get(&name) {
                out.write(data_url)?;
            }
        }
        Ok(())
    }
}

struct EqHelper;
impl HelperDef for EqHelper {
    fn call<'reg: 'rc, 'rc>(
        &self,
        h: &Helper<'rc>,
        r: &'reg Handlebars<'reg>,
        ctx: &'rc HbsContext,
        rc: &mut RenderContext<'reg, 'rc>,
        out: &mut dyn Output,
    ) -> HelperResult {
        let lhs = h.param(0).map(|v| value_to_string(v.value())).unwrap_or_default();
        let rhs = h.param(1).map(|v| value_to_string(v.value())).unwrap_or_default();
        let tmpl = if lhs == rhs { h.template() } else { h.inverse() };
        if let Some(t) = tmpl { t.render(r, ctx, rc, out)?; }
        Ok(())
    }
}

struct NotEqHelper;
impl HelperDef for NotEqHelper {
    fn call<'reg: 'rc, 'rc>(
        &self,
        h: &Helper<'rc>,
        r: &'reg Handlebars<'reg>,
        ctx: &'rc HbsContext,
        rc: &mut RenderContext<'reg, 'rc>,
        out: &mut dyn Output,
    ) -> HelperResult {
        let lhs = h.param(0).map(|v| value_to_string(v.value())).unwrap_or_default();
        let rhs = h.param(1).map(|v| value_to_string(v.value())).unwrap_or_default();
        let tmpl = if lhs != rhs { h.template() } else { h.inverse() };
        if let Some(t) = tmpl { t.render(r, ctx, rc, out)?; }
        Ok(())
    }
}

struct GtHelper;
impl HelperDef for GtHelper {
    fn call<'reg: 'rc, 'rc>(
        &self,
        h: &Helper<'rc>,
        r: &'reg Handlebars<'reg>,
        ctx: &'rc HbsContext,
        rc: &mut RenderContext<'reg, 'rc>,
        out: &mut dyn Output,
    ) -> HelperResult {
        let lhs = h.param(0).and_then(|v| v.value().as_f64()).unwrap_or(0.0);
        let rhs = h.param(1).and_then(|v| v.value().as_f64()).unwrap_or(0.0);
        let tmpl = if lhs > rhs { h.template() } else { h.inverse() };
        if let Some(t) = tmpl { t.render(r, ctx, rc, out)?; }
        Ok(())
    }
}

struct LtHelper;
impl HelperDef for LtHelper {
    fn call<'reg: 'rc, 'rc>(
        &self,
        h: &Helper<'rc>,
        r: &'reg Handlebars<'reg>,
        ctx: &'rc HbsContext,
        rc: &mut RenderContext<'reg, 'rc>,
        out: &mut dyn Output,
    ) -> HelperResult {
        let lhs = h.param(0).and_then(|v| v.value().as_f64()).unwrap_or(0.0);
        let rhs = h.param(1).and_then(|v| v.value().as_f64()).unwrap_or(0.0);
        let tmpl = if lhs < rhs { h.template() } else { h.inverse() };
        if let Some(t) = tmpl { t.render(r, ctx, rc, out)?; }
        Ok(())
    }
}

struct AnyHelper;
impl HelperDef for AnyHelper {
    fn call<'reg: 'rc, 'rc>(
        &self,
        h: &Helper<'rc>,
        r: &'reg Handlebars<'reg>,
        ctx: &'rc HbsContext,
        rc: &mut RenderContext<'reg, 'rc>,
        out: &mut dyn Output,
    ) -> HelperResult {
        let any_truthy = h.params().iter().any(|p| !is_falsy(p.value()));
        let tmpl = if any_truthy { h.template() } else { h.inverse() };
        if let Some(t) = tmpl { t.render(r, ctx, rc, out)?; }
        Ok(())
    }
}

struct ContainsFieldHelper;
impl HelperDef for ContainsFieldHelper {
    fn call<'reg: 'rc, 'rc>(
        &self,
        h: &Helper<'rc>,
        r: &'reg Handlebars<'reg>,
        ctx: &'rc HbsContext,
        rc: &mut RenderContext<'reg, 'rc>,
        out: &mut dyn Output,
    ) -> HelperResult {
        let list = h.param(0).map(|v| v.value()).unwrap_or(&Value::Null);
        let field = h.param(1).and_then(|v| v.value().as_str()).unwrap_or("");
        let contains = if let Value::Array(items) = list {
            items.iter().any(|item| !is_falsy(item.get(field).unwrap_or(&Value::Null)))
        } else { false };
        let tmpl = if contains { h.template() } else { h.inverse() };
        if let Some(t) = tmpl { t.render(r, ctx, rc, out)?; }
        Ok(())
    }
}

struct ContainsAllHelper;
impl HelperDef for ContainsAllHelper {
    fn call<'reg: 'rc, 'rc>(
        &self,
        h: &Helper<'rc>,
        r: &'reg Handlebars<'reg>,
        ctx: &'rc HbsContext,
        rc: &mut RenderContext<'reg, 'rc>,
        out: &mut dyn Output,
    ) -> HelperResult {
        let list = h.param(0).map(|v| v.value()).unwrap_or(&Value::Null);
        let required: Vec<String> = h.params().iter().skip(1)
            .map(|p| value_to_string(p.value())).collect();
        let contains = if let Value::Array(items) = list {
            let text_vals: Vec<String> = items.iter()
                .filter_map(|v| v.as_str().map(|s| s.to_string())).collect();
            !required.is_empty() && required.iter().all(|r| text_vals.contains(r))
        } else { false };
        let tmpl = if contains { h.template() } else { h.inverse() };
        if let Some(t) = tmpl { t.render(r, ctx, rc, out)?; }
        Ok(())
    }
}

struct IsDefinedHelper;
impl HelperDef for IsDefinedHelper {
    fn call<'reg: 'rc, 'rc>(
        &self,
        h: &Helper<'rc>,
        r: &'reg Handlebars<'reg>,
        ctx: &'rc HbsContext,
        rc: &mut RenderContext<'reg, 'rc>,
        out: &mut dyn Output,
    ) -> HelperResult {
        let defined = h.param(0).map(|p| !matches!(p.value(), Value::Null)).unwrap_or(false);
        let tmpl = if defined { h.template() } else { h.inverse() };
        if let Some(t) = tmpl { t.render(r, ctx, rc, out)?; }
        Ok(())
    }
}

struct FilterHelper;
impl HelperDef for FilterHelper {
    fn call<'reg: 'rc, 'rc>(
        &self,
        h: &Helper<'rc>,
        r: &'reg Handlebars<'reg>,
        ctx: &'rc HbsContext,
        rc: &mut RenderContext<'reg, 'rc>,
        out: &mut dyn Output,
    ) -> HelperResult {
        let list = h.param(0).map(|v| v.value().clone()).unwrap_or(Value::Null);
        let field = h.param(1).and_then(|v| v.value().as_str().map(|s| s.to_string()));
        let filter_value = h.param(2).and_then(|v| v.value().as_str().map(|s| s.to_string()));

        if let Value::Array(items) = list {
            for item in items {
                let item_val = field.as_deref().and_then(|f| get_nested_value(&item, f));
                let matches = match (&item_val, &filter_value) {
                    (Some(v), Some(fv)) => v == fv,
                    _ => false,
                };
                if matches {
                    if let Some(tmpl) = h.template() {
                        // Push a block context with the item as base value
                        let mut block = BlockContext::new();
                        block.set_base_value(item);
                        rc.push_block(block);
                        tmpl.render(r, ctx, rc, out)?;
                        rc.pop_block();
                    }
                }
            }
        }
        Ok(())
    }
}

/// The `block` helper renders a named partial if registered, otherwise the block content.
/// Implements the template-inheritance pattern from Java Handlebars.
struct BlockHelper;
impl HelperDef for BlockHelper {
    fn call<'reg: 'rc, 'rc>(
        &self,
        h: &Helper<'rc>,
        r: &'reg Handlebars<'reg>,
        ctx: &'rc HbsContext,
        rc: &mut RenderContext<'reg, 'rc>,
        out: &mut dyn Output,
    ) -> HelperResult {
        let block_name = h.param(0).and_then(|v| v.value().as_str()).unwrap_or("");
        if r.has_template(block_name) {
            let rendered = r.render(block_name, ctx.data())
                .map_err(|e| RenderError::from(RenderErrorReason::NestedError(Box::new(e))))?;
            out.write(&rendered)?;
        } else if let Some(t) = h.template() {
            t.render(r, ctx, rc, out)?;
        }
        Ok(())
    }
}

/// Render a named template with JSON data.
pub fn render_template(hbs: &Handlebars, template_name: &str, data: &Value) -> Result<String> {
    hbs.render(template_name, data)
        .with_context(|| format!("Failed to render template: {template_name}"))
}
