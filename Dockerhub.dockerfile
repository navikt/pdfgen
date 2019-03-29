FROM navikt/java:11
COPY pdfgen-*-all.jar app.jar

ENV JAVA_OPTS='-Dlogback.configurationFile=logback-remote.xml'
ENV DISABLE_PDF_GET="true"
