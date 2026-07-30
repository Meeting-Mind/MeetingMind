# Runtime Service Call Boundaries

## Status

- Scope: current NonProd V2 runtime on ECS Fargate.
- Browser-facing resource endpoints are orchestrated by Core during the gradual MSA phase.
- A logical BFF resilience class (`AI`, `LIVEKIT`) does not imply that the destination service owns the public endpoint or JWT audience.

## Synchronous Call Matrix

| Caller | Destination | Purpose | Authentication / authorization | Network boundary |
| --- | --- | --- | --- | --- |
| Browser | CloudFront → BFF | `/api/v1/*`, auth/session, Meeting/Space/AI/STT control | Secure HttpOnly BFF session cookie + CSRF for state changes | Same origin only; no MeetingMind access token in Browser |
| Browser | LiveKit Cloud | WebRTC media | Meeting-scoped short-lived LiveKit participant token issued after Core ACL | Direct provider connection |
| BFF | Auth | signup/login/Google/refresh/revoke/JWKS-related auth flow | BFF SPIFFE mTLS; credential/token exists only in the internal contract | BFF SG → Auth SG `8082` |
| BFF | Core | every current Browser-facing resource endpoint, including logical AI and LiveKit/STT control routes | BFF SPIFFE mTLS + `meetingmind-core` access JWT; Core performs current RBAC/ACL | BFF SG → Core SG `8080` |
| Core | Auth | target JWKS fetch | Core SPIFFE mTLS; JWT validation stays local between cache refreshes | Core SG → Auth SG `8082` |
| Core | Realtime STT | start/stop, active session, transcript/status/partials | Core SPIFFE mTLS; Core validates Meeting ACL before the call | Core SG → STT SG `8083` |
| Core | AI | Meeting/Project chat, report/task/knowledge generation | Core SPIFFE mTLS through AI Envoy; Core applies permission prefilter and sends scoped sources | Core SG → AI SG `8000` |
| Core | LiveKit Cloud | participant token and room control | Server-side LiveKit API key/secret after Meeting ACL | NAT HTTPS |
| Realtime STT | LiveKit Cloud | start/stop Track Egress | Server-side LiveKit API key/secret | NAT HTTPS |
| LiveKit Egress | CloudFront → ALB → Realtime STT | `/ws/egress-audio/{sessionId}` PCM stream | Session-bound short-lived HMAC query token; no Browser session/JWT | Only the WebSocket path is public to STT |
| Realtime STT | Soniox / OpenAI | realtime transcription and configured fallback | Provider secret in STT task only | NAT HTTPS/WSS |
| AI | OpenAI | text generation and embedding | Provider secret in AI task only | NAT HTTPS |

## Data Ownership

| Data | Authoritative owner | Read path |
| --- | --- | --- |
| Browser session / encrypted Token Bundle | BFF / Valkey + KMS | BFF only |
| User identity, credential, AuthSession, refresh lineage | Auth DB | Auth internal API; no cross-DB read |
| Space, Meeting, participant ACL, report, task, knowledge | Core DB | Core after access JWT and current RBAC/ACL checks |
| Live transcription session, status, speaker, segment | Realtime STT DB | Core→STT internal API after Meeting ACL |
| AI vector/index/runtime state | AI DB | AI internal implementation after Core supplies allowed scope |

## Known Transitional Boundary

- Live dialogue reads authoritative STT status, final segments and partials through Core; it must not read only the empty Core compatibility transcript row in remote mode.
- Core assembles report/task/Meeting AI transcript context from its derived transcript projection. After an authorized stop succeeds, Core pulls the full authoritative STT snapshot over mTLS and atomically replaces the meeting projection while preserving STT speaker and segment IDs.
- Identical terminal-stop retries are no-ops. A first `COMPLETED` transition enqueues the transcript embedding generation; a changed snapshot that was already completed enqueues one `FULL_REINDEX` generation. STT never writes Core DB and AI never reads STT DB directly.
- In remote mode Core also reconciles only bounded local candidates (`PROCESSING`, `FAILED`, or `COMPLETED` without segments) against the authoritative STT snapshot. This is an internal Core→STT mTLS loop, exposes no Browser endpoint, skips non-terminal snapshots, and reuses the same atomic projection and embedding-generation rules.
