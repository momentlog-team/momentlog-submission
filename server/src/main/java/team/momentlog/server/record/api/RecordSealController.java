package team.momentlog.server.record.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import team.momentlog.server.record.application.RecordSealService;
import team.momentlog.server.record.application.SealConflictException;
import team.momentlog.server.record.application.VerifyResult;
import team.momentlog.server.record.model.RecordSeal;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/records")
public class RecordSealController {

    private final RecordSealService recordSealService;

    public RecordSealController(RecordSealService recordSealService) {
        this.recordSealService = recordSealService;
    }

    /** 차량 앱이 기록 저장 직후 원본 해시를 봉인한다. */
    @PostMapping("/{recordId}/seal")
    public RecordSealResponse.SealDetail seal(@PathVariable String recordId,
                                              @Valid @RequestBody RecordSealRequest.Seal request) {
        RecordSeal seal = recordSealService.seal(recordId, request.sha256(), request.formatVersion(), request.deviceCreatedAt());
        return RecordSealResponse.SealDetail.from(seal);
    }

    /** 제출된 문서의 해시가 봉인된 원본과 같은지 확인한다(QR 확인 페이지가 호출). */
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
