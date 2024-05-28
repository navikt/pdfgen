FROM gcr.io/distroless/java21-debian12

RUN groupadd -r --gid 1069 apprunner && useradd -r --uid 1069 -g apprunner apprunner

WORKDIR /app

COPY build/libs/app-*.jar app.jar
COPY fonts fonts
COPY templates templates
COPY resources resources
ENV JDK_JAVA_OPTIONS="-Dlogback.configurationFile=logback-remote.xml"
ENV DISABLE_PDF_GET="true"
ENV ENABLE_HTML_ENDPOINT="false"

EXPOSE 8080

RUN chown -R apprunner /app
USER apprunner

CMD [ "app.jar" ]
