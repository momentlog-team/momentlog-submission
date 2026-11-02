package team.momentlog.server.device.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 기록을 보낸 차량 앱. 봉인 요청이 올 때 처음 보는 기기면 만들고, 이후에는 마지막 접속 시각만 갱신한다.
 * 기기 토큰은 앱이 설치 때 만든 난수이고, 서버는 처음 받은 토큰의 해시를 기억해 이후 요청을 확인한다(처음 본 토큰을 믿음).
 */
@Entity
@Table(name = "t_device")
public class Device {

    public enum Plan { free, subscribed }

    @Id
    @Column(name = "device_id", length = 64)
    private String deviceId;

    /** 기기 토큰 해시. 토큰을 보내기 전의 앱 버전으로 등록된 기기는 비어 있고, 처음 토큰을 보낼 때 채운다. */
    @Column(name = "token_sha256", columnDefinition = "char(64)")
    private String tokenSha256;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan", length = 16, nullable = false)
    private Plan plan;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    protected Device() {
    }

    private Device(String deviceId, String tokenSha256, Plan plan, Instant createdAt) {
        this.deviceId = deviceId;
        this.tokenSha256 = tokenSha256;
        this.plan = plan;
        this.createdAt = createdAt;
        this.lastSeenAt = createdAt;
    }

    public static Device firstSeen(String deviceId, Instant now) {
        return new Device(deviceId, null, Plan.free, now);
    }

    public static Device firstSeen(String deviceId, String tokenSha256, Instant now) {
        return new Device(deviceId, tokenSha256, Plan.free, now);
    }

    public boolean hasToken() {
        return tokenSha256 != null;
    }

    /** 토큰이 맞는지 본다. 아직 토큰이 없는 기기면 이 토큰으로 묶는다. */
    public boolean authenticate(String candidateSha256) {
        if (tokenSha256 == null) {
            tokenSha256 = candidateSha256.toLowerCase();
            return true;
        }
        return tokenSha256.equalsIgnoreCase(candidateSha256);
    }

    public void seen(Instant now) {
        this.lastSeenAt = now;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public Plan getPlan() {
        return plan;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }
}
