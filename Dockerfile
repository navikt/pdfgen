FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-25

ARG APP_VERSION=unknown

WORKDIR /app

COPY build/install/pdfgen/lib /lib

ENV APP_VERSION=${APP_VERSION} \
    DISABLE_PDF_GET=true

EXPOSE 8080
ENTRYPOINT ["/usr/bin/java", "-cp", "/lib/*", "no.nav.pdfgen.ApplicationKt"]
