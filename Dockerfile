FROM rust:1-slim-bookworm AS builder

WORKDIR /build

RUN apt-get update && apt-get install -y --no-install-recommends \
    pkg-config \
    && rm -rf /var/lib/apt/lists/*

COPY Cargo.toml Cargo.lock* ./
COPY src-rs/ src-rs/

RUN cargo build --release

FROM debian:bookworm-slim

RUN apt-get update && apt-get install -y --no-install-recommends \
    chromium \
    ca-certificates \
    fonts-liberation \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=builder /build/target/release/pdfgen /app/pdfgen

COPY templates /app/templates
COPY fonts /app/fonts
COPY resources /app/resources

ENV CHROME_BINARY="/usr/bin/chromium"
ENV DISABLE_PDF_GET="true"
ENV ENABLE_HTML_ENDPOINT="false"

EXPOSE 8080

USER nobody

ENTRYPOINT ["/app/pdfgen"]
