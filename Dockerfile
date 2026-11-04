FROM gcr.io/distroless/java25-debian13@sha256:d72c7b79a8c4babd6a69cf00979c9eed7f87e3e2b7d6871c6a0e9f91ff626cea

WORKDIR /app

COPY build/install/*/lib /lib

ENV JDK_JAVA_OPTIONS="--enable-native-access=ALL-UNNAMED -Dlogback.configurationFile=logback.xml"
ENV DISABLE_PDF_GET="true"
ENV ENABLE_HTML_ENDPOINT="false"

EXPOSE 8080

USER nonroot

ENTRYPOINT ["java", "-cp", "/lib/*", "no.nav.pdfgen.ApplicationKt"]
