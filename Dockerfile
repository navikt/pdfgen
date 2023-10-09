FROM gcr.io/distroless/java17@sha256:17459919f2ccb3439155da0b4f42ddf08cb022b2a3e1a4b9491d425dfcc31e7e

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
