FROM gcr.io/distroless/java25-debian13@sha256:c21a83a991d8bb70f8b58dcc9df75ef0c03f03a3c260d0a67cb706f6407afd08

WORKDIR /app

COPY build/install/*/lib /lib

ENV JDK_JAVA_OPTIONS="-Dlogback.configurationFile=logback.xml"
ENV DISABLE_PDF_GET="true"
ENV ENABLE_HTML_ENDPOINT="false"

EXPOSE 8080

USER nonroot

ENTRYPOINT ["java", "-cp", "/lib/*", "no.nav.pdfgen.ApplicationKt"]
