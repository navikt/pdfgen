#FROM openjdk:11-jdk as builder
#WORKDIR /
#COPY . .
#RUN ./gradlew build shadowJar

FROM navikt/java:11
#COPY --from=builder /build/libs/pdfgen-*-all.jar app.jar
COPY build/libs/pdfgen-*.jar app.jar
COPY fonts fonts
COPY templates templates
COPY resources resources
ENV JAVA_OPTS='-Dlogback.configurationFile=logback-remote.xml'
ENV DISABLE_PDF_GET="true"
