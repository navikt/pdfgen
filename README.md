# PdfGen

[![Build Status](https://travis-ci.org/navikt/pdfgen.svg?branch=master)](https://travis-ci.org/navikt/pdfgen)

Repository for PdfGen, an application written in Kotlin used to create PDFs

## Technologies & Tools

* Kotlin
* Gradle
* Ktor
* JUnit
* Handlebars

## Getting started
### Build and run tests
`./gradlew shadowJar`

Running the application locally enables a GET endpoint at `/api/v1/genpdf/<application>/<template>`
which looks for test data at `data/<application>/<template>.json` and outputs a PDF to your browser.
Additionally, the template folder will be fetched on every request, and reflect any changes made since the last GET,
making this ideal for developing new templates for your application.

The template and data directory structure both follow the `<application>/<template>` structure.

### Notes on developing templates on Windows
It is a known issue that pdfgen's output documents look different depending on whether the template
has `\r\n` or `\n` as line endings. Therefore, it is strongly recommended to configure Git to not convert newlines, as well as ensure that your editor ends its lines with LF (`\n`) and not CRLF (`\r\n`), as the former will accurately show what your
templates will look like in production.

## Contact us
### Code/project related questions can be sent to
* Kevin Sillerud, `kevin.sillerud@nav.no`

### For NAV employees
We are also available on the slack channel #integrasjon for internal communication.
