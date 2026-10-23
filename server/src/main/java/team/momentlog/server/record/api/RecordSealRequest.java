package team.momentlog.server.record.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;

public final class RecordSealRequest {

    private static final String SHA256 = "^[0-9a-fA-F]{64}$";

    private RecordSealRequest() {
    }

    public record Seal(
            @NotBlank @Pattern(regexp = SHA256) String sha256,
            int formatVersion,
            Instant deviceCreatedAt
    ) {
    }

    public record Verify(
            @NotBlank @Pattern(regexp = SHA256) String sha256
    ) {
    }
}
