# PdfGen

![Bygg og deploy](https://github.com/navikt/pdfgen/workflows/Bygg%20og%20deploy/badge.svg)

Repository for PdfGen, an application written in Kotlin used to create PDFs

## Technologies & Tools

* Kotlin
* Gradle
* Ktor
* Spek
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
* Andreas Nilsen, `andreas.nilsen@nav.no`
* Sebastian Knudsen, `sebastian.knudsen@nav.no`
* Tia Firing, `tia.firing@nav.no`
* Jonas Henie, `jonas.henie@nav.no`
* Mathias Hellevang, `mathias.hellevang@nav.no`

### For NAV employees
We are also available on the slack channel #integrasjon or #team-sykmelding for internal communication.
