package team.momentlog.server.device.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.momentlog.server.device.infrastructure.DeviceJpaRepository;
import team.momentlog.server.device.model.Device;

import java.time.Clock;
import java.time.Instant;

@Service
public class DeviceService {

    private final DeviceJpaRepository deviceJpaRepository;
    private final Clock clock;

    public DeviceService(DeviceJpaRepository deviceJpaRepository, Clock clock) {
        this.deviceJpaRepository = deviceJpaRepository;
        this.clock = clock;
    }

    /** 처음 보는 기기면 무료 플랜으로 등록하고, 아니면 마지막 접속 시각만 갱신한다. */
    @Transactional
    public Device touch(String deviceId) {
        Instant now = Instant.now(clock);
        return deviceJpaRepository.findById(deviceId)
                .map(device -> {
                    device.seen(now);
                    return device;
                })
                .orElseGet(() -> deviceJpaRepository.save(Device.firstSeen(deviceId, now)));
    }
}
