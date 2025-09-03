FROM gcr.io/distroless/java21-debian12@sha256:608e5ec41871d236b304c0728841d3a988aff3dbc19d60c5ae0240cdb3654c86

WORKDIR /app

COPY build/install/*/lib /lib

ENV JDK_JAVA_OPTIONS="-Dlogback.configurationFile=logback.xml"
ENV DISABLE_PDF_GET="true"
ENV ENABLE_HTML_ENDPOINT="false"

EXPOSE 8080

USER nonroot

ENTRYPOINT ["java", "-cp", "/lib/*", "no.nav.pdfgen.ApplicationKt"]
