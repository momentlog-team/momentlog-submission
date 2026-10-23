package team.momentlog.server.record.api;

import team.momentlog.server.record.application.VerifyResult;
import team.momentlog.server.record.model.RecordSeal;

import java.time.Instant;

public final class RecordSealResponse {

    private RecordSealResponse() {
    }

    public record SealDetail(String recordId, String sha256, Instant sealedAt) {
        static SealDetail from(RecordSeal seal) {
            return new SealDetail(seal.getRecordId(), seal.getSha256(), seal.getSealedAt());
        }
    }

    public record VerifyDetail(VerifyResult.Status status, Instant sealedAt) {
        static VerifyDetail from(VerifyResult result) {
            return new VerifyDetail(result.status(), result.sealedAt());
        }
    }
}
