package team.momentlog.server.document.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import team.momentlog.server.record.application.VerifyResult;

import java.time.Instant;

/** 문서 대조 이력. 누가 언제 어떤 해시로 확인했는지 남긴다(결과와 무관하게 모두). */
@Entity
@Table(name = "t_verify_log")
public class VerifyLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "document_no", length = 32, nullable = false)
    private String documentNo;

    @Column(name = "submitted_sha256", columnDefinition = "char(64)", nullable = false)
    private String submittedSha256;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", length = 16, nullable = false)
    private VerifyResult.Status result;

    @Column(name = "verified_at", nullable = false)
    private Instant verifiedAt;

    protected VerifyLog() {
    }

    private VerifyLog(String documentNo, String submittedSha256, VerifyResult.Status result, Instant verifiedAt) {
        this.documentNo = documentNo;
        this.submittedSha256 = submittedSha256;
        this.result = result;
        this.verifiedAt = verifiedAt;
    }

    public static VerifyLog of(String documentNo, String submittedSha256, VerifyResult.Status result, Instant now) {
        return new VerifyLog(documentNo, submittedSha256.toLowerCase(), result, now);
    }

    public Long getId() {
        return id;
    }

    public String getDocumentNo() {
        return documentNo;
    }

    public String getSubmittedSha256() {
        return submittedSha256;
    }

    public VerifyResult.Status getResult() {
        return result;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }
}
