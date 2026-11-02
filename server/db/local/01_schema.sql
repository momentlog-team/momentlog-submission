-- 서버 DB 스키마(MySQL). 백엔드A 설계(10/28) 기준. 시각은 모두 UTC.
-- mysql 프로필은 ddl-auto: validate 라서 엔티티와 이 파일이 어긋나면 서버가 뜨지 않는다.
USE momentlog;

-- 보낸 기기. plan은 free / subscribed
CREATE TABLE IF NOT EXISTS t_device (
    device_id    VARCHAR(64) NOT NULL,
    token_sha256 CHAR(64)    NULL COMMENT '기기 토큰 해시',
    plan         VARCHAR(16) NOT NULL DEFAULT 'free' COMMENT 'free / subscribed',
    created_at   DATETIME(6) NOT NULL,
    last_seen_at DATETIME(6) NOT NULL,
    PRIMARY KEY (device_id)
);

-- 원본 해시 봉인. sealed_at(서버 수신 시각)이 기준 시각. 3년 보관(공유·문서가 남아 있으면 그 뒤까지)
CREATE TABLE IF NOT EXISTS t_record_seal (
    record_id         VARCHAR(64) NOT NULL,
    device_id         VARCHAR(64) NULL,
    sha256            CHAR(64)    NOT NULL COMMENT '원본 해시',
    format_version    INT         NOT NULL,
    device_created_at DATETIME(6) NULL COMMENT '차량 시계',
    sealed_at         DATETIME(6) NOT NULL COMMENT '서버 수신 = 기준 시각',
    PRIMARY KEY (record_id),
    CONSTRAINT fk_record_seal_device FOREIGN KEY (device_id) REFERENCES t_device (device_id)
);

-- QR로 여는 문서 공유. body는 원본 바이트(봉인 해시와 같아야 만든다), statement_json은 진술(봉인 대상 아님)
-- expires_at(30일)이 지나면 정리 작업이 body·statement_json·place_text를 비우고 purged_at을 남긴다
CREATE TABLE IF NOT EXISTS t_record_share (
    share_id       VARCHAR(36)  NOT NULL,
    token_sha256   CHAR(64)     NOT NULL COMMENT 'QR 토큰 해시',
    record_id      VARCHAR(64)  NOT NULL,
    body           MEDIUMBLOB   NULL COMMENT '원본 바이트, 만료 뒤 NULL',
    statement_json TEXT         NULL COMMENT '진술, 봉인 대상 아님',
    place_text     VARCHAR(255) NULL,
    created_at     DATETIME(6)  NOT NULL,
    opened_at      DATETIME(6)  NULL,
    expires_at     DATETIME(6)  NOT NULL,
    purged_at      DATETIME(6)  NULL COMMENT '만료돼 본문을 지운 시각',
    PRIMARY KEY (share_id),
    UNIQUE KEY uk_record_share_token (token_sha256),
    KEY ix_record_share_expires (expires_at),
    CONSTRAINT fk_record_share_record FOREIGN KEY (record_id) REFERENCES t_record_seal (record_id)
);

-- 발급한 PDF. pdf_sha256이 09 화면(원본 대조)의 비교 대상. 3년 보관
-- record_at·record_lead·place_text는 공유 본문이 지워진 뒤에도 09 화면을 보여 주려고 발급 때 복사한 값
CREATE TABLE IF NOT EXISTS t_report (
    report_id   VARCHAR(36)  NOT NULL,
    document_no VARCHAR(32)  NOT NULL COMMENT 'SG-YYYYMMDD-HHMMSS',
    share_id    VARCHAR(36)  NOT NULL,
    record_id   VARCHAR(64)  NOT NULL,
    pdf_sha256  CHAR(64)     NOT NULL COMMENT '09 비교 대상',
    file_name   VARCHAR(255) NOT NULL,
    byte_size   INT          NOT NULL,
    page_count  INT          NOT NULL,
    storage_key VARCHAR(255) NOT NULL,
    issued_at   DATETIME(6)  NOT NULL,
    record_at   DATETIME(6)  NULL COMMENT '감지 시각(0초)',
    record_lead VARCHAR(255) NULL COMMENT '경위 첫 문장',
    place_text  VARCHAR(255) NULL,
    PRIMARY KEY (report_id),
    UNIQUE KEY uk_report_document_no (document_no),
    CONSTRAINT fk_report_share FOREIGN KEY (share_id) REFERENCES t_record_share (share_id),
    CONSTRAINT fk_report_record FOREIGN KEY (record_id) REFERENCES t_record_seal (record_id)
);

-- 문서 대조 이력. 90일 보관
CREATE TABLE IF NOT EXISTS t_verify_log (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    document_no      VARCHAR(32) NOT NULL,
    submitted_sha256 CHAR(64)    NOT NULL,
    result           VARCHAR(16) NOT NULL COMMENT 'MATCH / MISMATCH / NOT_FOUND',
    verified_at      DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY ix_verify_log_verified (verified_at)
);
