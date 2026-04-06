FROM gcr.io/distroless/java25-debian13@sha256:9460bf4816a914f6d4f1368568b1f56a69edd5164d215b57194cd8844adbcdfd

WORKDIR /app

COPY build/install/*/lib /lib

ENV JDK_JAVA_OPTIONS="-Dlogback.configurationFile=logback.xml"
ENV DISABLE_PDF_GET="true"
ENV ENABLE_HTML_ENDPOINT="false"

EXPOSE 8080

USER nonroot

ENTRYPOINT ["java", "-cp", "/lib/*", "no.nav.pdfgen.ApplicationKt"]
