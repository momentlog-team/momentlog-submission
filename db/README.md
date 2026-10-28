# DB 설계 (2026-10-28)

근거: 기획서 수정본 v2, 디자인 프로젝트의 `spec/00~02`·화면 01~11·`replay/*.json`(샘플 A·B·C), 이 저장소의 `core/`(10/27 합침·이어 붙임 반영본), `app/record/RecordStore.kt`, `server/record/`.

| 파일 | 내용 |
|---|---|
| `app/schema.sql` | 차량 앱 내부 DB (Room/SQLite 목표안). 기록, 감지 마커, 합친 기록, 원본 본문, 진술, 봉인 대기열, QR, 월간 점검, 06번 목록 뷰 |
| `server/schema.sql` | 서버 DB (MySQL 8 / H2 MySQL 모드). 기기, 봉인, 공유 본문, PDF 발급, 원본 확인 이력 |
| `app/seed.sql`, `server/seed.sql` | 샘플 A·B·C + 예시 D. `tools/build_seed.py`가 만든다(직접 고치지 않음) |
| `tools/check_db.py` | 스키마 + 시드를 올려 화면·보관 규칙 기준 25개 항목 확인 |

```bash
# 디자인 프로젝트가 저장소 바로 위 폴더에 있을 때 (다르면 경로를 인자로)
python -I db/tools/build_seed.py ../replay ../designs/export/10-report-sample-A.pdf
python -I db/tools/check_db.py ../designs/export/10-report-sample-A.pdf
```

## 화면 ↔ 테이블

| 화면 | 앱 | 서버 |
|---|---|---|
| 01 대기 "기록 불가" | `vehicle_signal_support` | |
| 02 저장 알림 5종 | `record`(capturing), `record_trigger`(type·command_input·while_parked) | |
| 03 진술 / "나중에" | `statement`, `record.statement_state` | |
| 04 결과 | `record`(headline·focus_at·위치), `record_trigger`(마커), `record_payload`(그래프), `statement_event`(진술 사건) | |
| 05 QR / 열림 / 통신 없음 | `share.opened_at`, `record_seal.status='queued'` | `t_record_share` |
| 06 목록 | `v_record_list` (retention, delete_at, statement_missing, marker_count, merged_count) | |
| 07 주행 재개 | `statement_state='WRITING'` | |
| 08 다운로드 · 10 PDF | | `t_record_share`(본문·진술), `t_report` |
| 09 원본 확인 | | `t_report.pdf_sha256`, `t_verify_log` |
| 11 월간 · 구독 | `signal_health_daily`, `notice_log`, `record_seal`(서버 전송 정상) | `t_device.plan` |

## 지금 앱 JSON 파일(RecordStore) ↔ 테이블

| JSON 필드 | 테이블.컬럼 |
|---|---|
| `id`, `trigger`, `triggerAt` | `record.id`, `record_trigger`(is_anchor=1).type·at |
| `events[]` | `record_trigger` 행들 |
| `merged[]` | `record_merge` |
| `parked` | `record_trigger.while_parked` (command) |
| `snapshots[]` | `record_payload.body` (RecordHasher v2 본문 그대로) |
| `sha256`, `sealed` | `record_payload.sha256`, `record_seal.status` |
| `statement`, `plate`, `statementState` | `statement`(answer·plate), `record.statement_state` |
| `retained` | `record.user_kept_at` |

계기 이름: `IMPACT`↔impact, `HARD_STOP`↔hardBrake, `AIRBAG`↔airbag, `VOICE`↔command.

## 샘플 데이터

| 기록 | 0초 | 마커 | 진술 | 보관 | 봉인 |
|---|---|---|---|---|---|
| A 11.03 18:42:17, 43초 | 충격(가속도, 뒤) | 1 | DONE | 자동 (충격) | 완료, 공유·PDF 발급·확인 2회 |
| B 11.05 14:10:52, 3분 30초 | 기록해(음성) | 2 (−1:32 급제동 95→41), 앞 기록 1건 합침 | DONE + 진술 사건 2 | 자동 (진술) | 완료 (합쳐진 앞 기록 봉인도 서버에 남음) |
| C 11.07 09:15:33, 40초, 위치 없음 | 충격(가속도, 앞 왼쪽) | 1 | LATER → "진술 미작성" | 자동 (충격) | 대기 (통신 없음) |
| D 11.06 07:52:18, 50초 | 급정지 | 1 | NONE | 11.13 삭제 ("6일 뒤") | 완료 |

## 팀이 정해야 할 것

1. **충격 계기:** 기획서 v2는 충격(가속도·IMPACT_DETECTED·밀림)을 따로 감지하지만 core `Trigger`에는 `IMPACT`가 없고, 앱은 에어백만 "충격"으로 표시한다. 시드 본문은 `trigger=IMPACT`를 쓴다(제안 값).
2. **멈추지 않을 때 끝:** core는 감지 +60초(`MAX_WAIT_FOR_STOP_MILLIS`), 기획서 v2는 마지막 감지 +20초.
3. **급정지 기준:** core는 1.5초 안 8km/h + 초당 25km/h, 기획서 v2는 1.5초 안 20km/h.
4. **"진술 미작성" 표시:** 앱은 DONE이 아니면 전부, 시안 06의 D(질문 전)는 표시 안 함. 뷰는 LATER·WRITING만 표시.
5. **7일 기준 시각:** 앱은 감지 시각(triggerAt), 기획서는 "저장 후". 뷰는 saved_at 기준.
6. **서버에 본문을 둘지:** architecture.md는 "서버엔 해시만"이지만 08 웹·10 PDF를 만들려면 서버가 본문을 알아야 한다(기획서 v2 백엔드 A "기록 업로드"). → "휴대폰으로 받기"를 누른 기록만 올리고, 본문 SHA-256이 봉인값과 같을 때만 받고, `expires_at` 뒤 삭제. **보관 기간 미정**(시드는 30일).
7. **원본 확인 API:** 지금 `/verify`는 기록 해시를 비교하지만 09번은 PDF 파일 해시를 비교 → `t_report.pdf_sha256` 기준 API 필요. PDF 바닥의 "원본 지문"(기록 해시)과 09번 지문(PDF 해시)이 다른 값임을 화면 문구에서 구분해야 한다.
8. **봉인 시각 표기:** 10번 시안은 서버 저장 18:42:19인데 기록은 18:42:30(정지 +10초)에 닫힌다(시드는 18:42:31.5). 05번 "원본 시각은 저장 순간으로 고정"도 서버 수신 시각 기준과 맞지 않는다.
9. **문서 번호** `SG-YYYYMMDD-HHMMSS`는 기기끼리 겹칠 수 있다 → UNIQUE + 접미사.
10. **신호 값:** SDK 브레이크는 밟음 여부(Boolean)뿐인데 replay는 0~100%. 전조등(SDK OFF/ON/DRL ↔ replay off/auto/tail/low/high)과 와이퍼(OFF/ON/OTHER/SERVICE ↔ off/auto/low/high)도 다르다. 본문은 core `Snapshot`처럼 Boolean으로 줄여 저장한다.
