FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-25

WORKDIR /app

COPY build/install/*/lib /lib

ENV DISABLE_PDF_GET=true

EXPOSE 8080
ENTRYPOINT ["/usr/bin/java", "-cp", "/lib/*", "no.nav.pdfgen.ApplicationKt"]
