# pdfgen

[![Build master](https://github.com/navikt/pdfgen/actions/workflows/build.yml/badge.svg?branch=master)](https://github.com/navikt/pdfgen/actions/workflows/build.yml)

Repository for `pdfgen`, an application written in Kotlin used to create PDFs and HTMLs

## Technologies & Tools

* Kotlin
* Gradle
* Ktor
* Junit
* Handlebars
* VeraPDF-validation
* JDK 17

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

Check GitHub releases to find the latest `release` version 
Check [Github releases](https://github.com/navikt/pdfgen/releases) to find the latest `release` version

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

[navikt/flex-sykepengesoknad-pdfgen](https://github.com/navikt/flex-sykepengesoknad-pdfgen),[navikt/smpdfgen](https://github.com/smpdfgen), [navikt/smarbeidsgiver-pdfgen](https://github.com/navikt/smarbeidsgiver-pdfgen) is some good examples of how such a project can be set up.

### Helpers
[navikt/pdfgen/blob/master/src/main/kotlin/no/nav/pdfgen/template/Helpers.kt](https://github.com/navikt/pdfgen/blob/master/src/main/kotlin/no/nav/pdfgen/template/Helpers.kt) contains a collection of customs Helpers which implements functionality that is not part of the Handlebars language itself.

## Developing pdfgen

### Build and run tests
`./gradlew shadowJar`

Running the application locally enables a GET endpoint at `/api/v1/genpdf/<application>/<template>`
which looks for test data at `data/<application>/<template>.json` and outputs a PDF to your browser.
Additionally, the template folder will be fetched on every request, and reflect any changes made since the last GET,
making this ideal for developing new templates for your application.

The template and data directory structure both follow the `<application>/<template>` structure.

To enable HTML document support, use the environment variable `ENABLE_HTML_ENDPOINT=true`. This will enable the 
HTML endpoints on `/api/v1/genhtml/<application>/<template>`. 

### Upgrading the gradle wrapper
Find the newest version of gradle here: https://gradle.org/releases/ Then run this command:

```./gradlew wrapper --gradle-version $gradleVersjon```


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

For contact requests within the [@navikt](https://github.com/navikt) org, you can use the slack channel #pdfgen

If you need to contact anyone directly, please see contributors.

## ✏️ Contributing

To get started, please fork the repo and checkout a new branch. You can then build the library with the Gradle wrapper

```shell script
./gradlew shadowJar
```

See more info in [CONTRIBUTING.md](CONTRIBUTING.md)
