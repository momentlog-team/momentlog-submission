package team.momentlog.server.record.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.momentlog.server.device.application.DeviceService;
import team.momentlog.server.record.infrastructure.RecordSealJpaRepository;
import team.momentlog.server.record.model.RecordSeal;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Service
public class RecordSealService {

    private final RecordSealJpaRepository recordSealJpaRepository;
    private final DeviceService deviceService;
    private final Clock clock;

    public RecordSealService(RecordSealJpaRepository recordSealJpaRepository, DeviceService deviceService, Clock clock) {
        this.recordSealJpaRepository = recordSealJpaRepository;
        this.deviceService = deviceService;
        this.clock = clock;
    }

    /**
     * 해시를 봉인한다. 통신이 끊겼다가 다시 보내는 경우가 있으므로 같은 기록·같은 해시는 처음 봉인을 그대로 돌려준다.
     * 같은 기록 ID로 다른 해시가 오면 원본을 덮어쓰지 않고 거부한다.
     */
    @Transactional
    public RecordSeal seal(String recordId, String deviceId, String sha256, int formatVersion, Instant deviceCreatedAt) {
        if (deviceId != null && !deviceId.isBlank()) {
            deviceService.touch(deviceId);
        }
        Optional<RecordSeal> existing = recordSealJpaRepository.findById(recordId);
        if (existing.isPresent()) {
            if (existing.get().matches(sha256)) {
                return existing.get();
            }
            throw new SealConflictException(recordId);
        }
        return recordSealJpaRepository.save(RecordSeal.seal(recordId, deviceId, sha256, formatVersion, deviceCreatedAt, Instant.now(clock)));
    }

    @Transactional(readOnly = true)
    public VerifyResult verify(String recordId, String sha256) {
        return recordSealJpaRepository.findById(recordId)
                .map(seal -> seal.matches(sha256) ? VerifyResult.match(seal) : VerifyResult.mismatch(seal))
                .orElseGet(VerifyResult::notFound);
    }
}
