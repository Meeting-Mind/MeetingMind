# Grafana Provisioning

Grafana가 기동할 때 datasource와 dashboard를 자동 등록하기 위한 파일이다.

## 구성

| 경로 | 역할 |
| --- | --- |
| `provisioning/datasources/prometheus.yml` | Prometheus datasource 등록. UID `meetingmind-prometheus`는 dashboard JSON이 참조하므로 바꾸지 않는다 |
| `provisioning/dashboards/meetingmind.yml` | `dashboards/` 디렉터리의 JSON을 자동 등록 |
| `dashboards/meetingmind-ai.json` | AI Overview + RAG/Embedding |
| `dashboards/meetingmind-bff.json` | Downstream guard + 브라우저 트래픽 |
| `dashboards/meetingmind-stt-live.json` | STT 전사, LiveKit 토큰 발급, 회의록 확정 |

## 사용

Grafana 컨테이너에 두 경로를 마운트한다.

```
./infra/grafana/provisioning -> /etc/grafana/provisioning
./infra/grafana/dashboards   -> /etc/grafana/dashboards
```

Prometheus 주소는 `PROMETHEUS_URL` 환경변수로 바꾼다. 기본값은 `http://prometheus:9090`이다.

## 지표 이름 확인 상태

**AI (검증 완료)** — 실제 `GET /metrics` 출력과 대조해 8종 전부 존재를 확인했다.

- `meetingmind_ai_requests_total{endpoint,outcome}`
- `meetingmind_ai_request_duration_ms_bucket{endpoint}`
- `meetingmind_ai_request_source_count_bucket{endpoint}`
- `meetingmind_ai_provider_total_ms_bucket{provider,api_style}`
- `meetingmind_ai_provider_tokens_total{provider,direction}`
- `meetingmind_ai_rag_retrieval_duration_ms_bucket{scope}`
- `meetingmind_ai_rag_retrieval_result_count_bucket{scope}`
- `meetingmind_ai_embedding_queue{status}`

**BFF (코드 기준, 실물 미확인)** — Micrometer 등록 코드에서 이름과 tag를 확인했고, Prometheus 노출명은 Micrometer 변환 규칙(dot -> underscore, Counter에 `_total`, Timer에 `_seconds_count`/`_seconds_sum`)을 적용했다. 실행 중인 BFF의 `/actuator/prometheus`는 인증이 걸려 있어 직접 대조하지 못했다.

## 알려진 문제

`@SpringBootTest` 환경에서 BFF `/actuator/prometheus`는 **Prometheus 노출 형식이 아닌 텍스트**를 반환한다(dot 이름 + `value=` 형태). 기존 `BffHealthEndpointTest.exposesPrometheusMetrics`는 "비어 있지 않음"만 단정해 이를 잡지 못한다.

이 상태에서는 dashboard가 쓰는 BFF 지표 이름을 테스트로 고정할 수 없다. 이름이 어긋나면 dashboard는 오류 없이 **빈 패널**이 되므로 조용히 실패한다. 실행 환경의 노출 형식 확인과 이름 고정 테스트는 후속 과제로 남긴다.

**Backend STT/LiveKit (검증 완료)** — `BackendMetricNamesTest`가 Prometheus 노출명을 고정한다. dashboard 쿼리에서 뽑은 이름과 테스트가 고정한 이름을 대조해 누락이 없음을 확인했다.

- `meetingmind_stt_transcription_start_seconds_{count,sum}{outcome}`
- `meetingmind_stt_transcription_stop_seconds_{count,sum}{outcome}`
- `meetingmind_livekit_token_issue_seconds_{count,sum}{outcome}`
- `meetingmind_report_confirm_seconds_{count,sum}{outcome}`
