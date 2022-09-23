FROM gcr.io/distroless/java17@sha256:2460346ea882bc4fb880363a4aa046c4567c4f095f89078a84965547d0794b15

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
