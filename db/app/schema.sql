-- 순간기록 차량 앱 내부 DB (Room / SQLite 목표안)  · 2026-10-28
-- 지금 앱은 기록당 JSON 파일(app/record/RecordStore.kt)을 쓴다. 필드 대응은 db/README.md.
-- 기록 본문(조작·위치)과 진술은 차 안에 둔다. 서버에는 해시와, 사용자가 "휴대폰으로 받기"를 누른 기록만 간다.
-- 시각은 모두 epoch ms(INTEGER). 화면 표시 시각 = at + tz_offset_min.
PRAGMA foreign_keys = ON;

-- 차종별 신호 지원 여부. 0이면 01·04·10번에 "기록 불가".
CREATE TABLE vehicle_signal_support (
  signal      TEXT PRIMARY KEY CHECK (signal IN ('speed','brake','turnSignal','steering','gear','headlight','wiper','airbag','accel','impactSignal','location')),
  supported   INTEGER NOT NULL CHECK (supported IN (0,1)),
  checked_at  INTEGER NOT NULL
);

-- 기록 1건 (core IncidentRecord). 합침은 새 id로 만들고 앞 기록은 지운다(record_merge에 흔적).
CREATE TABLE record (
  id               TEXT PRIMARY KEY,                 -- UUID
  state            TEXT NOT NULL CHECK (state IN ('capturing','saved')),
  is_demo          INTEGER NOT NULL DEFAULT 0,       -- 재생·목데이터("시연 데이터" 표시)
  tz_offset_min    INTEGER NOT NULL DEFAULT 540,
  window_start_at  INTEGER NOT NULL,                 -- 저장 구간 시작 (자동 −30초, 수동 −3분)
  window_end_at    INTEGER,                          -- capturing 중엔 NULL
  stopped_at       INTEGER,                          -- 차가 멈춘 시각. 안 멈췄으면 NULL
  focus_at         INTEGER NOT NULL,                 -- 04번 "그 순간의 내 차" 시점 (A=충격, B=−1:32 급제동)
  place_text       TEXT,                             -- NULL = 위치 확인 불가
  lat              REAL,
  lng              REAL,
  heading_deg      INTEGER CHECK (heading_deg BETWEEN 0 AND 359),
  headline         TEXT,                             -- 경위 한 줄 (본문에서 만든 파생값, 해시 대상 아님)
  detail           TEXT,
  statement_state  TEXT NOT NULL DEFAULT 'NONE'
                   CHECK (statement_state IN ('NONE','LATER','WRITING','DONE')),
                   -- RecordStore.StatementState. LATER="나중에", WRITING=진술 중 출발(07번)
  user_kept_at     INTEGER,                          -- "보관하기" 누른 시각 (RecordStore retained)
  created_at       INTEGER NOT NULL,                 -- 첫 감지 시각
  saved_at         INTEGER,                          -- 구간이 닫혀 저장된 시각
  CHECK (window_end_at IS NULL OR window_end_at > window_start_at),
  CHECK ((lat IS NULL) = (lng IS NULL))
);

-- 감지 시점 = 그래프 마커 (core IncidentEvent). 기록마다 하나가 0초(is_anchor=1).
CREATE TABLE record_trigger (
  id                INTEGER PRIMARY KEY AUTOINCREMENT,
  record_id         TEXT NOT NULL REFERENCES record(id) ON DELETE CASCADE,
  type              TEXT NOT NULL CHECK (type IN ('impact','hardBrake','airbag','command')),
  at                INTEGER NOT NULL,
  is_anchor         INTEGER NOT NULL DEFAULT 0 CHECK (is_anchor IN (0,1)),
  impact_source     TEXT CHECK (impact_source IN ('accel','vehicleSignal','pushed')),   -- impact만
  impact_direction  TEXT CHECK (impact_direction IN ('front','rear','left','right','frontLeft','frontRight','rearLeft','rearRight')),
  peak_g            REAL,
  speed_from_kmh    REAL,                            -- hardBrake: 95→41
  speed_to_kmh      REAL,
  command_input     TEXT CHECK (command_input IN ('voice','button')),                   -- command만
  while_parked      INTEGER CHECK (while_parked IN (0,1)),                              -- 1 = 사후 저장 (savedWhileParked)
  CHECK (type = 'impact'  OR (impact_source IS NULL AND impact_direction IS NULL)),
  CHECK (type = 'command' OR (command_input IS NULL AND while_parked IS NULL))
);
CREATE UNIQUE INDEX ux_trigger_anchor ON record_trigger(record_id) WHERE is_anchor = 1;
CREATE INDEX ix_trigger_record ON record_trigger(record_id, at);

-- 이 기록에 합쳐져 지운 앞선 기록 (IncidentRecord.mergedRecordIds). 앞 기록의 봉인은 서버에 그대로 남는다.
CREATE TABLE record_merge (
  record_id         TEXT NOT NULL REFERENCES record(id) ON DELETE CASCADE,
  merged_record_id  TEXT NOT NULL,
  PRIMARY KEY (record_id, merged_record_id)
);

-- 원본 본문: 해시를 계산한 바이트 그대로(RecordHasher.canonical). 진술은 넣지 않는다.
CREATE TABLE record_payload (
  record_id       TEXT PRIMARY KEY REFERENCES record(id) ON DELETE CASCADE,
  format_version  INTEGER NOT NULL,                  -- RecordHasher.FORMAT_VERSION (지금 2)
  body            BLOB NOT NULL,
  sha256          TEXT NOT NULL CHECK (length(sha256) = 64),
  created_at      INTEGER NOT NULL
);

-- 진술 (센서 기록 아님). replay voice의 answer / plate와 같은 구분.
CREATE TABLE statement (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  record_id   TEXT NOT NULL REFERENCES record(id) ON DELETE CASCADE,
  kind        TEXT NOT NULL CHECK (kind IN ('answer','plate')),
  text        TEXT NOT NULL,
  said_at     INTEGER NOT NULL,
  edited      INTEGER NOT NULL DEFAULT 0 CHECK (edited IN (0,1))   -- "고치기"로 바꿈
);
CREATE INDEX ix_statement_record ON statement(record_id, said_at);

-- 진술에서 나온 사건(그래프의 회색 점선 "진술 · 앞으로 끼어듦"). 센서 마커와 섞지 않는다.
CREATE TABLE statement_event (
  id            INTEGER PRIMARY KEY AUTOINCREMENT,
  record_id     TEXT NOT NULL REFERENCES record(id) ON DELETE CASCADE,
  statement_id  INTEGER REFERENCES statement(id) ON DELETE SET NULL,
  at            INTEGER NOT NULL,                    -- 대략 시각 (예: 0초 −2분 10초)
  label         TEXT NOT NULL
);

-- 봉인 전송 대기열 겸 결과. 통신이 없으면 queued로 두고 연결되면 보낸다(05번 통신 없음).
CREATE TABLE record_seal (
  record_id         TEXT PRIMARY KEY REFERENCES record(id) ON DELETE CASCADE,
  sha256            TEXT NOT NULL CHECK (length(sha256) = 64),
  format_version    INTEGER NOT NULL,
  status            TEXT NOT NULL CHECK (status IN ('queued','sealed','conflict')),
  attempts          INTEGER NOT NULL DEFAULT 0,
  last_attempt_at   INTEGER,
  last_error        TEXT,
  server_sealed_at  INTEGER                          -- 서버가 돌려준 sealedAt
);
CREATE INDEX ix_seal_queue ON record_seal(status) WHERE status = 'queued';

-- QR 발급 (05번). opened_at이 차면 "휴대폰에서 열었어요 · 18:46:02".
CREATE TABLE share (
  id           TEXT PRIMARY KEY,                     -- 서버 share_id
  record_id    TEXT NOT NULL REFERENCES record(id) ON DELETE CASCADE,
  url          TEXT NOT NULL,
  document_no  TEXT,                                 -- SG-YYYYMMDD-HHMMSS
  created_at   INTEGER NOT NULL,
  expires_at   INTEGER,
  opened_at    INTEGER
);

-- 11번 월간 안내: 날짜별 신호 수신 여부, 안내 노출 기록.
CREATE TABLE signal_health_daily (
  day       TEXT NOT NULL,                           -- 'YYYY-MM-DD'
  signal    TEXT NOT NULL,
  received  INTEGER NOT NULL CHECK (received IN (0,1)),
  PRIMARY KEY (day, signal)
);
CREATE TABLE notice_log (
  kind      TEXT NOT NULL CHECK (kind IN ('monthly','subscribe')),
  period    TEXT NOT NULL,                           -- 'YYYY-MM'
  shown_at  INTEGER NOT NULL,
  PRIMARY KEY (kind, period)
);

-- 06번 목록. 보관 규칙: 충격·에어백 감지 또는 진술 완료 → 자동 보관, 보관하기 → 사용자 보관, 나머지 저장 7일 뒤 삭제.
-- "진술 미작성"은 LATER·WRITING만 (시안 06의 D처럼 질문 전 NONE은 표시 안 함 — README 결정 필요 참고).
CREATE VIEW v_record_list AS
SELECT x.*,
       CASE WHEN x.has_impact = 1 OR x.statement_state = 'DONE' THEN 'keepAuto'
            WHEN x.user_kept_at IS NOT NULL THEN 'keepUser'
            ELSE 'expiring' END AS retention,
       CASE WHEN x.has_impact = 0 AND x.statement_state <> 'DONE' AND x.user_kept_at IS NULL
            THEN x.saved_at + 7 * 86400000 END AS delete_at,
       x.statement_state IN ('LATER','WRITING') AS statement_missing
FROM (
  SELECT r.id, r.is_demo, r.headline, r.place_text, r.statement_state, r.user_kept_at, r.saved_at,
         a.type AS anchor_type, a.at AS anchor_at, a.while_parked, r.tz_offset_min,
         r.window_end_at - r.window_start_at AS duration_ms,
         (SELECT COUNT(*) FROM record_trigger t WHERE t.record_id = r.id) AS marker_count,
         (SELECT COUNT(*) FROM record_merge m WHERE m.record_id = r.id) AS merged_count,
         EXISTS (SELECT 1 FROM record_trigger t WHERE t.record_id = r.id AND t.type IN ('impact','airbag')) AS has_impact
  FROM record r JOIN record_trigger a ON a.record_id = r.id AND a.is_anchor = 1
  WHERE r.state = 'saved'
) x;

-- 7일 삭제 작업 (앱 시작·하루 1회):
-- DELETE FROM record WHERE id IN (SELECT id FROM v_record_list WHERE retention = 'expiring' AND delete_at <= :now);
