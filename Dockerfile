FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-25

WORKDIR /app

COPY build/install/*/lib /lib

ENV JDK_JAVA_OPTIONS="--enable-native-access=ALL-UNNAMED -Dlogback.configurationFile=logback.xml" \
    DISABLE_PDF_GET=true \
    ENABLE_HTML_ENDPOINT=false

EXPOSE 8080

ENTRYPOINT ["/usr/bin/java", "-cp", "/lib/*", "no.nav.pdfgen.ApplicationKt"]
