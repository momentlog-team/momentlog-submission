package team.momentlog.server.document.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class DocumentRequest {

    private static final String SHA256 = "^[0-9a-fA-F]{64}$";

    private DocumentRequest() {
    }

    /**
     * @param body          원본. core `RecordHasher.canonical(record)` 문자열 그대로(UTF-8). 이 SHA-256이 봉인 해시와 같아야 한다.
     * @param statementJson 음성 진술(JSON 문자열). 봉인 대상 아님. 없으면 null
     * @param placeText     사고 장소 표시용 문구. 없으면 null
     */
    public record CreateShare(
            @NotBlank String body,
            String statementJson,
            @Size(max = 255) String placeText
    ) {
    }

    public record Verify(
            @NotBlank @Pattern(regexp = SHA256) String sha256
    ) {
    }
}
