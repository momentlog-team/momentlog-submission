package team.momentlog.server.device.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.momentlog.server.device.infrastructure.DeviceJpaRepository;
import team.momentlog.server.device.model.Device;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;

@Service
public class DeviceService {

    private final DeviceJpaRepository deviceJpaRepository;
    private final Clock clock;

    public DeviceService(DeviceJpaRepository deviceJpaRepository, Clock clock) {
        this.deviceJpaRepository = deviceJpaRepository;
        this.clock = clock;
    }

    /**
     * 토큰 없이 온 요청(토큰을 보내기 전 앱 버전). 처음 보는 기기면 무료 플랜으로 등록하고, 아니면 마지막 접속 시각만 갱신한다.
     * 이미 토큰이 묶인 기기 ID를 토큰 없이 쓰면 거부한다(남의 기기 ID 사칭 방지).
     */
    @Transactional
    public Device touch(String deviceId) {
        Instant now = Instant.now(clock);
        return deviceJpaRepository.findById(deviceId)
                .map(device -> {
                    if (device.hasToken()) {
                        throw new DeviceAuthException("기기 토큰이 필요합니다.");
                    }
                    device.seen(now);
                    return device;
                })
                .orElseGet(() -> deviceJpaRepository.save(Device.firstSeen(deviceId, now)));
    }

    /**
     * 기기 ID와 토큰을 확인한다. 처음 보는 기기면 이 토큰으로 등록하고, 토큰 없이 등록된 기기면 이 토큰으로 묶는다.
     * 토큰이 없거나 다르면 [DeviceAuthException].
     */
    @Transactional
    public Device authenticate(String deviceId, String token) {
        if (deviceId == null || deviceId.isBlank() || token == null || token.isBlank()) {
            throw new DeviceAuthException("기기 ID와 토큰이 필요합니다.");
        }
        Instant now = Instant.now(clock);
        String tokenSha256 = sha256(token);
        Device device = deviceJpaRepository.findById(deviceId)
                .orElseGet(() -> deviceJpaRepository.save(Device.firstSeen(deviceId, tokenSha256, now)));
        if (!device.authenticate(tokenSha256)) {
            throw new DeviceAuthException("기기 토큰이 맞지 않습니다.");
        }
        device.seen(now);
        return device;
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
