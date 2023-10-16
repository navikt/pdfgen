FROM gcr.io/distroless/java17@sha256:2f01c2ff0c0db866ed73085cf1bb5437dd162b48526f89c1baa21dd77ebb5e6d

WORKDIR /app

COPY build/libs/app-*.jar app.jar
COPY fonts fonts
COPY templates templates
COPY resources resources
ENV JDK_JAVA_OPTIONS="-Dlogback.configurationFile=logback-remote.xml"
ENV DISABLE_PDF_GET="true"
ENV ENABLE_HTML_ENDPOINT="false"

EXPOSE 8080
CMD [ "app.jar" ]
