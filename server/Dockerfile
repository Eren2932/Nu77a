# ---- build stage -------------------------------------------------------------
FROM golang:1.23-alpine AS builder

ARG VERSION=dev
ARG COMMIT=none
ARG BUILT_AT=unknown

WORKDIR /src

# Dependencies first: this layer is cached until go.mod/go.sum change.
COPY go.mod go.sum* ./
RUN go mod download || true

COPY . .
RUN go mod tidy

# CGO off => a fully static binary that runs on scratch/alpine unchanged.
RUN CGO_ENABLED=0 GOOS=linux go build \
    -trimpath \
    -ldflags="-s -w -X main.version=${VERSION} -X main.commit=${COMMIT} -X main.builtAt=${BUILT_AT}" \
    -o /out/nuva-server ./cmd/nuva-server

# ---- runtime stage ----------------------------------------------------------
FROM alpine:3.20

RUN apk add --no-cache ca-certificates tzdata curl \
    && addgroup -g 10001 -S nuva \
    && adduser -u 10001 -S nuva -G nuva

COPY --from=builder /out/nuva-server /usr/local/bin/nuva-server

RUN mkdir -p /data/media && chown -R nuva:nuva /data

USER nuva
WORKDIR /data
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
    CMD curl -fsS http://127.0.0.1:8080/healthz || exit 1

ENTRYPOINT ["/usr/local/bin/nuva-server"]
