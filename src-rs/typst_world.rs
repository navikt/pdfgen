use std::collections::HashMap;
use std::path::{Path, PathBuf};

use anyhow::Result;
use typst::foundations::Bytes;
use typst::{Library, LibraryExt};
use typst::utils::LazyHash;
use typst_library::diag::{FileError, FileResult};
use typst_library::text::{Font, FontBook};
use typst_library::World;
use typst_syntax::{FileId, Source, VirtualPath};

static EMBEDDED_FONTS: &[&[u8]] = &[
    include_bytes!("../fonts/SourceSansPro-Regular.ttf"),
    include_bytes!("../fonts/SourceSansPro-Bold.ttf"),
];

/// A minimal Typst World implementation that:
/// - Provides the standard library
/// - Loads fonts from the fonts directory + embedded fallback fonts
/// - Serves a main `.typ` source and optional data as virtual files
pub struct PdfgenWorld {
    library: LazyHash<Library>,
    font_book: LazyHash<FontBook>,
    fonts: Vec<Font>,
    main_id: FileId,
    main_source: Source,
    /// Virtual files accessible by ID: template files and data
    virtual_files: HashMap<FileId, Bytes>,
    /// Physical file root for resolving relative paths in templates
    root: PathBuf,
}

impl PdfgenWorld {
    /// Create a new world for rendering a Typst source string with optional
    /// auxiliary files accessible via the virtual file system.
    ///
    /// `fonts_dir`: path to fonts directory (loaded in addition to embedded fonts)
    /// `main_path`: virtual path of the main document (e.g. `/main.typ`)
    /// `main_source`: the Typst source code to compile
    /// `virtual_files`: additional files (e.g. `data.json`) accessible by virtual path
    pub fn new(
        fonts_dir: &str,
        root: &Path,
        main_path: &str,
        main_source: String,
        virtual_files: HashMap<String, Bytes>,
    ) -> Result<Self> {
        let (fonts, font_book) = load_fonts(fonts_dir);

        let main_id = FileId::new(None, VirtualPath::new(main_path));
        let source = Source::new(main_id, main_source);

        let vfiles: HashMap<FileId, Bytes> = virtual_files
            .into_iter()
            .map(|(path, bytes)| (FileId::new(None, VirtualPath::new(&path)), bytes))
            .collect();

        Ok(Self {
            library: LazyHash::new(Library::default()),
            font_book: LazyHash::new(font_book),
            fonts,
            main_id,
            main_source: source,
            virtual_files: vfiles,
            root: root.to_path_buf(),
        })
    }
}

impl World for PdfgenWorld {
    fn library(&self) -> &LazyHash<Library> {
        &self.library
    }

    fn book(&self) -> &LazyHash<FontBook> {
        &self.font_book
    }

    fn main(&self) -> FileId {
        self.main_id
    }

    fn source(&self, id: FileId) -> FileResult<Source> {
        if id == self.main_id {
            return Ok(self.main_source.clone());
        }
        // Check virtual files first
        if let Some(bytes) = self.virtual_files.get(&id) {
            let text = std::str::from_utf8(bytes.as_slice())
                .map_err(|_| FileError::InvalidUtf8)?
                .to_string();
            return Ok(Source::new(id, text));
        }
        // Try resolving relative to the root
        let vpath = id.vpath();
        let physical = self.root.join(vpath.as_rootless_path());
        let text = std::fs::read_to_string(&physical)
            .map_err(|e| FileError::from_io(e, &physical))?;
        Ok(Source::new(id, text))
    }

    fn file(&self, id: FileId) -> FileResult<Bytes> {
        // Check virtual files
        if let Some(bytes) = self.virtual_files.get(&id) {
            return Ok(bytes.clone());
        }
        // Try resolving relative to the root
        let vpath = id.vpath();
        let physical = self.root.join(vpath.as_rootless_path());
        let bytes = std::fs::read(&physical)
            .map_err(|e| FileError::from_io(e, &physical))?;
        Ok(Bytes::new(bytes))
    }

    fn font(&self, index: usize) -> Option<Font> {
        self.fonts.get(index).cloned()
    }

    fn today(&self, offset: Option<i64>) -> Option<typst_library::foundations::Datetime> {
        let now = chrono::Local::now();
        let naive = if let Some(off) = offset {
            let utc = now.with_timezone(&chrono::Utc);
            (utc + chrono::Duration::hours(off)).naive_local()
        } else {
            now.naive_local()
        };
        typst_library::foundations::Datetime::from_ymd(
            naive.year(),
            naive.month() as u8,
            naive.day() as u8,
        )
    }
}

fn load_fonts(fonts_dir: &str) -> (Vec<Font>, FontBook) {
    let mut fonts: Vec<Font> = Vec::new();

    // Load fonts from the fonts directory
    if let Ok(entries) = std::fs::read_dir(fonts_dir) {
        for entry in entries.filter_map(|e| e.ok()) {
            let path = entry.path();
            if let Some(ext) = path.extension().and_then(|e| e.to_str()) {
                if matches!(ext.to_lowercase().as_str(), "ttf" | "otf" | "ttc") {
                    if let Ok(data) = std::fs::read(&path) {
                        let bytes = Bytes::new(data);
                        fonts.extend(Font::iter(bytes));
                    }
                }
            }
        }
    }

    // If no fonts were loaded from disk, use embedded fonts as fallback
    if fonts.is_empty() {
        for &font_data in EMBEDDED_FONTS {
            let bytes = Bytes::new(font_data.to_vec());
            fonts.extend(Font::iter(bytes));
        }
    }

    let font_book = FontBook::from_fonts(&fonts);
    (fonts, font_book)
}

/// Compile a Typst source document to PDF bytes.
///
/// `fonts_dir`: directory containing font files
/// `root`: base path for resolving template file includes
/// `main_path`: virtual path for the main document  
/// `main_source`: Typst source code
/// `virtual_files`: additional virtual files (e.g. data.json)
pub fn compile_to_pdf(
    fonts_dir: &str,
    root: &Path,
    main_path: &str,
    main_source: String,
    virtual_files: HashMap<String, Bytes>,
) -> Result<Vec<u8>> {
    let world = PdfgenWorld::new(fonts_dir, root, main_path, main_source, virtual_files)?;

    let result = typst::compile::<typst_library::layout::PagedDocument>(&world);
    let document = result
        .output
        .map_err(|errors| {
            let msgs: Vec<String> = errors
                .iter()
                .map(|e| e.message.to_string())
                .collect();
            anyhow::anyhow!("Typst compilation failed: {}", msgs.join("; "))
        })?;

    if !result.warnings.is_empty() {
        let warns: Vec<String> = result.warnings.iter().map(|w| w.message.to_string()).collect();
        tracing::warn!("Typst warnings: {}", warns.join("; "));
    }

    let pdf_bytes = typst_pdf::pdf(&document, &typst_pdf::PdfOptions::default())
        .map_err(|errors| {
            let msgs: Vec<String> = errors.iter().map(|e| e.message.to_string()).collect();
            anyhow::anyhow!("Typst PDF export failed: {}", msgs.join("; "))
        })?;

    Ok(pdf_bytes)
}

use chrono::Datelike;
