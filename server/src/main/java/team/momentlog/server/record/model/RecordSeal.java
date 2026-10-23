package team.momentlog.server.record.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 기록 원본의 "봉인". 차량 앱이 기록을 저장한 직후 원본 해시를 보내면 서버 시각과 함께 고정한다.
 * 기록 본문(위치·조작 내역)은 받지 않는다. 해시만 있으면 나중에 제출된 문서가 원본과 같은지 대조할 수 있다.
 */
@Entity
@Table(name = "t_record_seal")
public class RecordSeal {

    @Id
    @Column(name = "record_id", length = 64)
    private String recordId;

    @Column(name = "sha256", length = 64, nullable = false)
    private String sha256;

    @Column(name = "format_version", nullable = false)
    private int formatVersion;

    /** 차량이 기록을 만든 시각(차량 시계). 참고용. */
    @Column(name = "device_created_at")
    private Instant deviceCreatedAt;

    /** 서버가 해시를 받은 시각. 위변조 판단의 기준 시각. */
    @Column(name = "sealed_at", nullable = false)
    private Instant sealedAt;

    protected RecordSeal() {
    }

    private RecordSeal(String recordId, String sha256, int formatVersion, Instant deviceCreatedAt, Instant sealedAt) {
        this.recordId = recordId;
        this.sha256 = sha256;
        this.formatVersion = formatVersion;
        this.deviceCreatedAt = deviceCreatedAt;
        this.sealedAt = sealedAt;
    }

    public static RecordSeal seal(String recordId, String sha256, int formatVersion, Instant deviceCreatedAt, Instant now) {
        return new RecordSeal(recordId, sha256.toLowerCase(), formatVersion, deviceCreatedAt, now);
    }

    public boolean matches(String candidateSha256) {
        return candidateSha256 != null && sha256.equalsIgnoreCase(candidateSha256);
    }

    public String getRecordId() {
        return recordId;
    }

    public String getSha256() {
        return sha256;
    }

    public int getFormatVersion() {
        return formatVersion;
    }

    public Instant getDeviceCreatedAt() {
        return deviceCreatedAt;
    }

    public Instant getSealedAt() {
        return sealedAt;
    }
}
