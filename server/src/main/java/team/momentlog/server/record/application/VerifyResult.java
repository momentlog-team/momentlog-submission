package team.momentlog.server.record.application;

import team.momentlog.server.record.model.RecordSeal;

import java.time.Instant;

/** 원본 대조 결과. MATCH = 원본과 같음, MISMATCH = 원본과 다름(수정됨), NOT_FOUND = 봉인된 적 없음. */
public record VerifyResult(Status status, Instant sealedAt) {

    public enum Status { MATCH, MISMATCH, NOT_FOUND }

    static VerifyResult match(RecordSeal seal) {
        return new VerifyResult(Status.MATCH, seal.getSealedAt());
    }

    static VerifyResult mismatch(RecordSeal seal) {
        return new VerifyResult(Status.MISMATCH, seal.getSealedAt());
    }

    static VerifyResult notFound() {
        return new VerifyResult(Status.NOT_FOUND, null);
    }
}
