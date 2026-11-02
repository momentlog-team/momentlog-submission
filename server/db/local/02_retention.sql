-- 보관 기간 정리(2026-11-02)에 맞춘 변경. 01_schema.sql을 11/02 이전에 실행한 로컬 DB에만 한 번 실행한다.
-- 새로 만드는 DB는 01_schema.sql에 이미 들어 있다.
USE momentlog;

ALTER TABLE t_record_share
    MODIFY body MEDIUMBLOB NULL COMMENT '원본 바이트, 만료 뒤 NULL',
    ADD COLUMN purged_at DATETIME(6) NULL COMMENT '만료돼 본문을 지운 시각' AFTER expires_at,
    ADD KEY ix_record_share_expires (expires_at);

ALTER TABLE t_report
    ADD COLUMN record_at   DATETIME(6)  NULL COMMENT '감지 시각(0초)' AFTER issued_at,
    ADD COLUMN record_lead VARCHAR(255) NULL COMMENT '경위 첫 문장' AFTER record_at,
    ADD COLUMN place_text  VARCHAR(255) NULL AFTER record_lead;

ALTER TABLE t_verify_log
    ADD KEY ix_verify_log_verified (verified_at);
