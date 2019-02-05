FROM navikt/java:11
COPY build/libs/pdfgen-*-all.jar app.jar
ENV JAVA_OPTS='-Dlogback.configurationFile=logback-remote.xml'
ENV DISABLE_PDF_GET="true"

RUN mkdir out
COPY templates templates
COPY resources resources
COPY fonts fonts
