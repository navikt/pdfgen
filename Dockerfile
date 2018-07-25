FROM navikt/java:8
COPY build/install/* /app
ENV JAVA_OPTS="'-Dlogback.configurationFile=logback-remote.xml'"
ENV DISABLE_PDF_GET="true"

RUN mkdir out
COPY templates templates
COPY resources resources

ENTRYPOINT ["/app/bin/pdfgen"]
