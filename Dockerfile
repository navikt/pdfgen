FROM navikt/java:14
COPY build/libs/pdfgen-1.3.2-all.jar app.jar
COPY fonts fonts
COPY templates templates
COPY resources resources
ENV JAVA_OPTS='-Dlogback.configurationFile=logback-remote.xml'
ENV DISABLE_PDF_GET="true"
