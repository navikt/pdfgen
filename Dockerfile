FROM navikt/java:17
COPY build/libs/pdfgen-1.4.1-all.jar app.jar
COPY fonts fonts
COPY templates templates
COPY resources resources
ENV JAVA_OPTS='-Dlogback.configurationFile=logback-remote.xml'
ENV DISABLE_PDF_GET="true"
