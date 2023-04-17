FROM gcr.io/distroless/java17@sha256:901215ab3ae619500f184668461cf901830e7a9707f8f9c016d9c08d8060db5a

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
