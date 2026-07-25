ARG GO_IMAGE=golang:1.26.5-alpine3.24@sha256:0178a641fbb4858c5f1b48e34bdaabe0350a330a1b1149aabd498d0699ff5fb2
FROM --platform=$BUILDPLATFORM ${GO_IMAGE} AS build

WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY cmd cmd
COPY internal internal

ARG TARGETOS
ARG TARGETARCH
RUN test "${TARGETOS}" = "linux" \
    && test "${TARGETARCH}" = "arm64" \
    && CGO_ENABLED=0 GOOS="${TARGETOS}" GOARCH="${TARGETARCH}" \
       go build -trimpath -ldflags="-s -w -buildid=" \
       -o /out/meetingmind-cert-loader ./cmd/cert-loader

FROM scratch

COPY --from=build /etc/ssl/certs/ca-certificates.crt /etc/ssl/certs/ca-certificates.crt
COPY --from=build /out/meetingmind-cert-loader /meetingmind-cert-loader

USER 0:0
ENTRYPOINT ["/meetingmind-cert-loader"]
