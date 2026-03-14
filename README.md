# pdfgen

[![Build main](https://github.com/navikt/pdfgen/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/navikt/pdfgen/actions/workflows/build.yml)

![GitHub Release](https://img.shields.io/github/v/release/navikt/pdfgen)


Repository for `pdfgen`, an application written in Rust used to create PDFs and HTMLs

## Technologies & Tools

* Rust
* Cargo
* Axum (HTTP server)
* Handlebars (template engine)
* Chromium/Chrome (PDF generation via headless browser)
* Prometheus (metrics)
* Docker

## Getting started

Most commonly, pdfgen is used as a base image alongside templates, fonts, additional resources, and potential test data to verify that valid PDFs get produced by the aforementioned templates.

In your own repository, create a Dockerfile with the following contents

```dockerfile
# Dockerfile
FROM ghcr.io/navikt/pdfgen:<release>

COPY templates /app/templates # handlebars templates
COPY fonts /app/fonts         # fonts to be embedded
COPY resources /app/resources # additional resources
```
If you need to specify your own worker group size, connection group size or call group size you may add these as environment variables in the 
Dockerfile. The variable names are WORKER_GROUP_SIZE, CONNECTION_GROUP_SIZE and CALL_GROUP_SIZE.


Check GitHub releases to find the latest `release` version 
Check [GitHub releases](https://github.com/navikt/pdfgen/releases) to find the latest `release` version

Set up the basic folder structure
```bash
mkdir {templates,fonts,resources,data}
```

Create subfolders in `templates` and `data`
```bash
mkdir {templates,data}/your_teamname # your_teamname can be anything, but it'll be a necessary part of the API later
```

* `templates/your_teamname/` should then be populated with your .hbs-templates. the names of these templates will also decide parts of the API paths
* `data/your_teamname/` should be populated with json files with names corresponding to a target .hbs-template, this can be used to test your PDFs during development of templates.

[navikt/flex-sykepengesoknad-pdfgen](https://github.com/navikt/flex-sykepengesoknad-pdfgen), [navikt/smpdfgen](https://github.com/navikt/smpdfgen), [navikt/smarbeidsgiver-pdfgen](https://github.com/navikt/smarbeidsgiver-pdfgen), [pale-2-pdfgen](https://github.com/navikt/pale-2-pdfgen) is some good examples of how such a project can be set up.

[navikt/helsearbeidsgiver-pdfgen](https://github.com/navikt/helsearbeidsgiver-pdfgen) is a project that has tests to ensure templates produce the expected PDF text content.

### Helpers
[https://github.com/navikt/pdfgen-core/blob/main/src/main/kotlin/no/nav/pdfgen/core/template/Helpers.kt](https://github.com/navikt/pdfgen-core/blob/main/src/main/kotlin/no/nav/pdfgen/core/template/Helpers.kt) contains a collection of customs Helpers which implements functionality that is not part of the Handlebars language itself.

## Developing pdfgen

### Build and run tests
```shell script
cargo build --release
```

Running the application locally enables a GET endpoint at `/api/v1/genpdf/<application>/<template>`
which looks for test data at `data/<application>/<template>.json` and outputs a PDF to your browser.
The template and data directory structure both follow the `<application>/<template>` structure.

To enable HTML document support, use the environment variable `ENABLE_HTML_ENDPOINT=true`. This will enable the 
HTML endpoints on `/api/v1/genhtml/<application>/<template>`. 

By default, pdfgen will load all assets (`templates`, `resources`, `data`) to memory on startup. Any change on files inside these folders will not be loaded before a restart of the application.

### Environment variables

| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_PORT` | `8080` | HTTP server port |
| `DISABLE_PDF_GET` | `false` (binary), `true` (Docker image) | Disable GET endpoint for PDF generation |
| `ENABLE_HTML_ENDPOINT` | `false` | Enable HTML generation endpoints |
| `DEV_MODE` | `false` | Reload templates on each request (development only) |
| `TEMPLATES_DIR` | `templates` | Directory containing Handlebars templates |
| `RESOURCES_DIR` | `resources` | Directory containing resource files (images) |
| `FONTS_DIR` | `fonts` | Directory containing fonts |
| `DATA_DIR` | `data` | Directory containing test data JSON files |
| `CHROME_BINARY` | auto-detect | Path to Chrome/Chromium binary for PDF generation |

### Release
We use default GitHub release. 
This project uses [semantic versioning](https://semver.org/) and does NOT prefix tags or release titles with `v` i.e. use `1.2.3` instead of `v1.2.3` 

See guide about how to release: [creating release github](
https://docs.github.com/en/repositories/releasing-projects-on-github/managing-releases-in-a-repository#creating-a-release)

## pdfgen 2.0

`pdfgen` 2.0 targets a new PDF standard, [PDF/A-2a](https://en.wikipedia.org/wiki/PDF/A#PDF/A-2), as well as [PDF/UA](https://en.wikipedia.org/wiki/PDF/UA).
The primary goal is to produce PDFs that are not only valid for archival, but also valid according to Universal Accessibility standards.
This introduces a few "breaking" changes, although they are not enforced by pdfgen; it is still possible to produce PDFs that don't achieve "Universal Accessibility" as defined by PDF/UA.

At the time of writing, none of the documentation exists to describe how to produce PDFs with proper UA for template writers.
As such, here be dragons!

A [licensed Adobe Acrobat DC](https://gist.github.com/karinaldw/1c4c321fe05bdc1e8996e00722d5317a#adobe-acrobat-dc-pdf) can help you check whether the PDFs you generate achieve UA. 
[PAC 3](https://www.access-for-all.ch/en/pdf-accessibility-checker.html) (Windows only) is another tool for UA verification.


## 👥 Contact

This project is currently maintained by the organisation [@navikt](https://github.com/navikt).

If you need to raise an issue or question about this library, please create an issue here and tag it with the appropriate label.

For contact requests within the [@navikt](https://github.com/navikt) org, you can use the Slack channel #pdfgen

If you need to contact anyone directly, please see [CODEOWNERS](CODEOWNERS)

## ✏️ Contributing

To get started, please fork the repo and checkout a new branch. You can then build the library with the Gradle wrapper

```shell script
./gradlew installDist
```

See more info in [CONTRIBUTING.md](CONTRIBUTING.md)
