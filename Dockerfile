FROM navikt/java:11
COPY build/libs/pdfgen-*-all.jar app.jar
COPY fonts fonts
COPY templates templates
COPY resources resources
ENV JAVA_OPTS='-Dlogback.configurationFile=logback-remote.xml'
ENV DISABLE_PDF_GET="true"