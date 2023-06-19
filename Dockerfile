FROM gcr.io/distroless/java17@sha256:052076466984fd56979c15a9c3b7433262b0ad9aae55bc0c53d1da8ffdd829c3

WORKDIR /app

COPY build/libs/pdfgen-2.0.0-all.jar app.jar
COPY fonts fonts
COPY templates templates
COPY resources resources
ENV JDK_JAVA_OPTIONS="-Dlogback.configurationFile=logback-remote.xml"
ENV DISABLE_PDF_GET="true"
ENV ENABLE_HTML_ENDPOINT="false"

EXPOSE 8080
CMD [ "app.jar" ]
