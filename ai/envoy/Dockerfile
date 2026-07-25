# Upstream Envoy distroless를 manifest digest로 고정한 AI mTLS sidecar 이미지.
# runtime pull은 이 이미지를 mirror한 전용 V2 ECR digest만 사용한다.
FROM envoyproxy/envoy:distroless-v1.38.3@sha256:574348fada8eb1130b448132287d76626dfb07525b16668075382f8e154a45a8

COPY envoy.yaml /etc/envoy/envoy.yaml

USER 10001:10001
ENTRYPOINT ["/usr/local/bin/envoy"]
CMD ["-c", "/etc/envoy/envoy.yaml", "--disable-hot-restart", "--log-level", "info"]
