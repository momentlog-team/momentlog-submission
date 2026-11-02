package team.momentlog.server.record.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import team.momentlog.server.device.api.DeviceHeaders;
import team.momentlog.server.record.application.RecordSealService;
import team.momentlog.server.record.application.SealConflictException;
import team.momentlog.server.record.application.VerifyResult;
import team.momentlog.server.record.model.RecordSeal;

import java.util.Map;

@Tag(name = "기록 봉인", description = "사고 기록 원본 해시 봉인과 위변조 확인")
@RestController
@RequestMapping("/api/v1/records")
public class RecordSealController {

    private final RecordSealService recordSealService;

    public RecordSealController(RecordSealService recordSealService) {
        this.recordSealService = recordSealService;
    }

    /** 차량 앱이 기록 저장 직후 원본 해시를 봉인한다. */
    @Operation(summary = "원본 해시 봉인",
            description = "차량 앱이 기록 저장 직후 호출. 해시와 서버 시각을 고정하고, deviceId가 있으면 기기를 등록·갱신한다. 같은 해시 재전송은 처음 봉인을 돌려주고, 다른 해시는 409. "
                    + "X-Device-Token 헤더가 있으면 기기를 확인한다(처음 보는 토큰은 등록, 다르면 401). 토큰이 묶인 기기 ID를 토큰 없이 쓰면 401.")
    @PostMapping("/{recordId}/seal")
    public RecordSealResponse.SealDetail seal(@PathVariable String recordId,
                                              @RequestHeader(name = DeviceHeaders.DEVICE_TOKEN, required = false) String deviceToken,
                                              @Valid @RequestBody RecordSealRequest.Seal request) {
        RecordSeal seal = recordSealService.seal(recordId, request.deviceId(), deviceToken, request.sha256(), request.formatVersion(), request.deviceCreatedAt());
        return RecordSealResponse.SealDetail.from(seal);
    }

    /** 제출된 문서의 해시가 봉인된 원본과 같은지 확인한다(QR 확인 페이지가 호출). */
    @Operation(summary = "원본 대조",
            description = "제출된 문서의 해시가 봉인된 원본과 같은지 확인. MATCH(같음) / MISMATCH(고쳐짐) / NOT_FOUND(봉인된 적 없음).")
    @PostMapping("/{recordId}/verify")
    public RecordSealResponse.VerifyDetail verify(@PathVariable String recordId,
                                                  @Valid @RequestBody RecordSealRequest.Verify request) {
        VerifyResult result = recordSealService.verify(recordId, request.sha256());
        return RecordSealResponse.VerifyDetail.from(result);
    }

    @ExceptionHandler(SealConflictException.class)
    public ResponseEntity<Map<String, String>> conflict(SealConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("errorCode", "SEAL_CONFLICT", "message", e.getMessage()));
    }
}
