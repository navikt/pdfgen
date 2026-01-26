FROM gcr.io/distroless/java25-debian13@sha256:cc3bb44755599d4c25c26c43b05761eeb1da2e779172cee258c2202ca071abfa

WORKDIR /app

COPY build/install/*/lib /lib

ENV JDK_JAVA_OPTIONS="-Dlogback.configurationFile=logback.xml"
ENV DISABLE_PDF_GET="true"
ENV ENABLE_HTML_ENDPOINT="false"

EXPOSE 8080

USER nonroot

ENTRYPOINT ["java", "-cp", "/lib/*", "no.nav.pdfgen.ApplicationKt"]
