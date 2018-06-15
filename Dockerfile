FROM navikt/java:8
COPY build/install/* /app
ENV JAVA_OPTS="'-Dlogback.configurationFile=logback-remote.xml'"

ENTRYPOINT ["/app/bin/pdfgen"]
