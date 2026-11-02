# 구조

## 데이터 흐름

```
[차량]                          [앱 app/]                                  [서버 server/]
Brake·TurnSignal·Steering ─┐
Light·Wiper·Airbag ────────┼─▶ SignalSource ─▶ SignalState(마지막 값)
차량 속도(PERF_VEHICLE_SPEED)┘        │
                                     ▼ 100ms마다 스냅샷
                               IncidentRecorder (core)
                                 ├ SnapshotBuffer: 최근 30초만 메모리에
                                 └ HardStopDetector: 급정지 감지
                                     │ 급정지 / "기록해" / 에어백
                                     ▼ 직후 10초까지 모은 뒤
                               IncidentRecord ─▶ RecordStore(차 안 JSON)
                                     │
                                     ├─ RecordHasher.sha256 ──────────▶ POST /api/v1/records/{id}/seal
                                     │                                   (해시 + 서버 시각만 저장)
                                     └─ IncidentNarrator ─▶ 경위 문장 ─▶ 화면 / (2단계) 문서 PDF + QR
                                                                          │
                                                       QR 확인 페이지 ──▶ POST /api/v1/records/{id}/verify
```

- 위치·조작 내역은 차 안에 둔다. 서버에는 봉인 해시만 간다. 단 QR 화면을 연 기록만 원본과 진술을 올리고(공유), 공유 만료 뒤 서버가 지운다(아래 보관 기간).
- 시연(`ReplaySource`)과 실차(`VehicleSignalClient`)는 같은 `SignalState`를 채우므로 기록 로직은 하나다.

## 모듈

| 모듈 | 언어 | 내용 |
|---|---|---|
| `core` | Kotlin(JVM) | Android 의존 없음. 단위 테스트로 검증 |
| `app` | Kotlin(Android) | SDK 연결, 포그라운드 서비스, 화면 |
| `server` | Java 17, Spring Boot | `record` 도메인: api / application / model / infrastructure |

## 보관 기간 (2026-11-02 결정)

| 데이터 | 기간 | 처리 |
|---|---|---|
| QR 링크, 공유 원본 본문·진술·장소, PDF 파일 | 30일 (`momentlog.share.ttl`) | 링크는 410. 본문·진술·장소를 비우고 PDF 파일을 지운다 |
| 발급 문서 지문(`t_report`), 원본 봉인(`t_record_seal`) | 3년 (`momentlog.retention.proof-ttl`) | 이 기간 동안 원본 확인(`/v/{문서번호}`)이 된다. 봉인은 그 기록의 공유·문서가 다 지워진 뒤 지운다 |
| 원본 확인 이력(`t_verify_log`) | 90일 (`momentlog.retention.verify-log-ttl`) | 행 삭제 |
| 차 안의 사고 아닌 기록 | 감지 시각부터 7일 | 앱 `RecordStore.purgeExpired` |

서버 정리는 `RetentionService`가 매일 04:30(한국 시각)에 한다.

## 기준값 (1차, 실측 후 조정)

| 항목 | 값 | 위치 |
|---|---|---|
| 버퍼 길이 | 직전 30초 + 직후 10초 | `IncidentRecorder` |
| 급정지 | 1.5초 안, 8km/h 이상 감소 + 초당 25km/h(약 0.7g) 이상 | `HardStopDetector` |
| 재감지 대기 | 10초 | `HardStopDetector` |
| 스냅샷 주기 | 100ms | `RecorderService` |
| 핸들 정면 판정 | ±5° | `IncidentNarrator` |

## 역할 분담 (앱 1, 백엔드 2)

| 담당 | 다음 작업 |
|---|---|
| 앱 | app 첫 빌드·에뮬레이터 실행, Virtual Console로 신호 수신 확인, check*Capabilities 로그, 정차 후 음성 진술(TTS→STT), "기록해" 음성 트리거, 주행 중 화면 제한 |
| 백엔드 A | 봉인 재전송(오프라인 대기열) API 정리, 보관 정책, 경위 문장 규칙 다듬기(core) |
| 백엔드 B | 사고 정리 문서(경위 문장 + 그래프 PDF), QR 확인 웹페이지, 시연 시나리오 데이터 다양화 |

## 1차 접수(11/15)까지 목표

급정지 저장 → 경위 문장 → 원본 봉인 → 문서 → QR 확인. 이 한 흐름이 에뮬레이터에서 돌아가는 영상과 APK.
