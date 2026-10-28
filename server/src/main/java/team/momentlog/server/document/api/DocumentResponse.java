package team.momentlog.server.document.api;

import team.momentlog.server.document.application.DocumentService;
import team.momentlog.server.record.application.VerifyResult;

import java.time.Instant;

public final class DocumentResponse {

    private DocumentResponse() {
    }

    /** token은 이 응답에서만 받을 수 있다. QR 주소는 `/r/{token}`. */
    public record ShareDetail(String shareId, String recordId, String token, String path, Instant expiresAt) {
        static ShareDetail from(DocumentService.CreatedShare created) {
            return new ShareDetail(created.share().getShareId(), created.share().getRecordId(), created.token(),
                    "/r/" + created.token(), created.share().getExpiresAt());
        }
    }

    public record VerifyDetail(String documentNo, VerifyResult.Status status) {
    }
}
