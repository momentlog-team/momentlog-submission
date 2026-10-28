package team.momentlog.server.device.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** 기록을 보낸 차량 앱. 봉인 요청이 올 때 처음 보는 기기면 만들고, 이후에는 마지막 접속 시각만 갱신한다. */
@Entity
@Table(name = "t_device")
public class Device {

    public enum Plan { free, subscribed }

    @Id
    @Column(name = "device_id", length = 64)
    private String deviceId;

    /** 기기 토큰 해시. 기기 인증을 붙이기 전까지는 비어 있다. */
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

    private Device(String deviceId, Plan plan, Instant createdAt) {
        this.deviceId = deviceId;
        this.plan = plan;
        this.createdAt = createdAt;
        this.lastSeenAt = createdAt;
    }

    public static Device firstSeen(String deviceId, Instant now) {
        return new Device(deviceId, Plan.free, now);
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
