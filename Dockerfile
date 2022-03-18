FROM gcr.io/distroless/java17@sha256:51312565c020a77583a9c699eb4d92b99d9629f4109a333d49a7c4f7ed5f9f82

WORKDIR /app
COPY build/libs/pdfgen-1.4.3-all.jar app.jar
COPY fonts fonts
COPY templates templates
COPY resources resources
ENV JAVA_OPTS='-Dlogback.configurationFile=logback-remote.xml'
ENV DISABLE_PDF_GET="true"

EXPOSE 8080
CMD [ "app.jar"]