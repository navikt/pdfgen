FROM gcr.io/distroless/java21-debian12

WORKDIR /app

COPY build/libs/app-*.jar app.jar
# Oppretter tomme kataloger
COPY empty /app/fonts
COPY empty /app/templates
COPY empty /app/resources
ENV JDK_JAVA_OPTIONS="-Dlogback.configurationFile=logback.xml"
ENV DISABLE_PDF_GET="true"
ENV ENABLE_HTML_ENDPOINT="false"

EXPOSE 8080

USER nonroot

CMD [ "app.jar" ]
