FROM gcr.io/distroless/java25-debian13@sha256:817363ac3b3efab113afa288fe4d4d0fba6daaf02e59ae8813e7573dbc556212

WORKDIR /app

COPY build/install/*/lib /lib

ENV JDK_JAVA_OPTIONS="-Dlogback.configurationFile=logback.xml"
ENV DISABLE_PDF_GET="true"
ENV ENABLE_HTML_ENDPOINT="false"

EXPOSE 8080

USER nonroot

ENTRYPOINT ["java", "-cp", "/lib/*", "no.nav.pdfgen.ApplicationKt"]
