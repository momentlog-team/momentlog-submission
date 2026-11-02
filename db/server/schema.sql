-- 순간기록 서버 DB (MySQL 8 / H2 MODE=MySQL)  · 2026-10-28
-- 시각은 UTC (ClockConfig = systemUTC). 개인정보(실명·연락처)는 받지 않는다.

-- 앱 설치 단위의 익명 기기. 무료 문서 1건 / 구독 판단(11번)에 쓴다.
CREATE TABLE t_device (
  device_id      VARCHAR(36)  NOT NULL PRIMARY KEY,
  token_sha256   CHAR(64)     NOT NULL,              -- 기기 인증 토큰의 해시 (원문 저장 안 함)
  plan           VARCHAR(16)  NOT NULL DEFAULT 'free' CHECK (plan IN ('free','subscribed')),
  created_at     TIMESTAMP(3) NOT NULL,
  last_seen_at   TIMESTAMP(3)
);

-- 원본 봉인. 지금 엔티티(record/model/RecordSeal.java)와 같고 device_id만 더했다(선택).
-- 합친 기록은 새 record_id로 봉인하므로 같은 id에 다른 해시가 오면 지금처럼 409.
CREATE TABLE t_record_seal (
  record_id          VARCHAR(64)  NOT NULL PRIMARY KEY,
  device_id          VARCHAR(36),
  sha256             VARCHAR(64)  NOT NULL,
  format_version     INT          NOT NULL,
  device_created_at  TIMESTAMP(3),                   -- 차량 시계, 참고용
  sealed_at          TIMESTAMP(3) NOT NULL,          -- 서버 수신 시각 = 위변조 판단 기준
  FOREIGN KEY (device_id) REFERENCES t_device (device_id)
);

-- "휴대폰으로 받기"를 누른 기록만 올라오는 본문. body의 SHA-256이 봉인값과 같아야 받는다.
-- 08번 웹과 10번 PDF는 이 본문으로 만든다.
CREATE TABLE t_record_share (
  share_id         VARCHAR(36)  NOT NULL PRIMARY KEY,
  token_sha256     CHAR(64)     NOT NULL UNIQUE,     -- QR URL 토큰의 해시
  record_id        VARCHAR(64)  NOT NULL,
  body             MEDIUMBLOB   NOT NULL,            -- 원본 바이트 (봉인 해시 대상)
  statement_json   TEXT,                             -- 진술·상대 번호·진술 사건 (센서 기록 아님, 봉인 대상 아님)
  place_text       VARCHAR(200),                     -- NULL = 위치 확인 불가
  created_at       TIMESTAMP(3) NOT NULL,
  opened_at        TIMESTAMP(3),                     -- 휴대폰에서 처음 연 시각 → 차량 05번 "열었어요"
  expires_at       TIMESTAMP(3) NOT NULL,
  FOREIGN KEY (record_id) REFERENCES t_record_seal (record_id)
);
CREATE INDEX ix_share_record ON t_record_share (record_id);
CREATE INDEX ix_share_expires ON t_record_share (expires_at);

-- 발급한 PDF. 09번 원본 확인은 이 pdf_sha256과 휴대폰에서 계산한 지문을 비교한다.
CREATE TABLE t_report (
  report_id     VARCHAR(36)  NOT NULL PRIMARY KEY,
  document_no   VARCHAR(40)  NOT NULL UNIQUE,        -- SG-YYYYMMDD-HHMMSS (겹치면 -2, -3 …)
  share_id      VARCHAR(36)  NOT NULL,
  record_id     VARCHAR(64)  NOT NULL,
  pdf_sha256    CHAR(64)     NOT NULL,
  file_name     VARCHAR(80)  NOT NULL,               -- 순간기록_YYYYMMDD_HHMMSS.pdf
  byte_size     INT          NOT NULL,
  page_count    INT          NOT NULL DEFAULT 1,
  storage_key   VARCHAR(200) NOT NULL,               -- "원본 PDF 받기"용 보관 위치
  issued_at     TIMESTAMP(3) NOT NULL,
  FOREIGN KEY (share_id) REFERENCES t_record_share (share_id),
  FOREIGN KEY (record_id) REFERENCES t_record_seal (record_id)
);
CREATE INDEX ix_report_record ON t_report (record_id);

-- 원본 확인 이력 (시연·감사용). 파일 자체나 IP는 저장하지 않는다.
CREATE TABLE t_verify_log (
  id                BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  document_no       VARCHAR(40),
  submitted_sha256  CHAR(64)     NOT NULL,
  result            VARCHAR(16)  NOT NULL CHECK (result IN ('MATCH','MISMATCH','NOT_FOUND')),
  verified_at       TIMESTAMP(3) NOT NULL
);
