package team.momentlog.server.document.model;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

/**
 * QR로 여는 문서 공유. 차량 앱이 원본 바이트를 올리면 봉인된 해시와 같은지 확인한 뒤에만 만든다.
 * QR에는 토큰 원문이 들어가고, DB에는 토큰 해시만 둔다. 진술은 사고 뒤에 덧붙이는 내용이라 봉인 대상이 아니다.
 * 만료되면 [purge]로 본문·진술·장소를 비운다. 행은 발급 문서(t_report)가 가리키므로 남긴다.
 */
@Entity
@Table(name = "t_record_share")
public class RecordShare {

    @Id
    @Column(name = "share_id", length = 36)
    private String shareId;

    @Column(name = "token_sha256", columnDefinition = "char(64)", nullable = false, unique = true)
    private String tokenSha256;

    @Column(name = "record_id", length = 64, nullable = false)
    private String recordId;

    /** 원본 바이트. SHA-256이 봉인 해시와 같다. 만료 뒤에는 null. */
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "body", columnDefinition = "mediumblob")
    private byte[] body;

    @Column(name = "statement_json", columnDefinition = "text")
    private String statementJson;

    @Column(name = "place_text", length = 255)
    private String placeText;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** QR로 처음 연 시각. */
    @Column(name = "opened_at")
    private Instant openedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** 만료돼 본문·진술을 지운 시각. */
    @Column(name = "purged_at")
    private Instant purgedAt;

    protected RecordShare() {
    }

    private RecordShare(String shareId, String tokenSha256, String recordId, byte[] body, String statementJson,
                        String placeText, Instant createdAt, Instant expiresAt) {
        this.shareId = shareId;
        this.tokenSha256 = tokenSha256;
        this.recordId = recordId;
        this.body = body;
        this.statementJson = statementJson;
        this.placeText = placeText;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public static RecordShare create(String shareId, String tokenSha256, String recordId, byte[] body,
                                     String statementJson, String placeText, Instant now, Instant expiresAt) {
        return new RecordShare(shareId, tokenSha256, recordId, body, statementJson, placeText, now, expiresAt);
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    /** 만료된 공유의 원본·진술·장소를 지운다. 위치와 주행 내역이 서버에 남지 않게 한다. */
    public void purge(Instant now) {
        body = null;
        statementJson = null;
        placeText = null;
        purgedAt = now;
    }

    /** 진술·장소까지 같은 공유인지. 같으면 새로 만들지 않고 재사용한다. */
    public boolean hasSameContent(String statementJson, String placeText) {
        return Objects.equals(this.statementJson, statementJson) && Objects.equals(this.placeText, placeText);
    }

    public boolean matchesToken(String candidateSha256) {
        return tokenSha256.equalsIgnoreCase(candidateSha256);
    }

    public void open(Instant now) {
        if (openedAt == null) {
            openedAt = now;
        }
    }

    public String getShareId() {
        return shareId;
    }

    public String getRecordId() {
        return recordId;
    }

    public byte[] getBody() {
        return body;
    }

    public String getStatementJson() {
        return statementJson;
    }

    public String getPlaceText() {
        return placeText;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getPurgedAt() {
        return purgedAt;
    }
}
