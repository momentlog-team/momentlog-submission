package team.momentlog.server.document.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import team.momentlog.server.device.api.DeviceHeaders;
import team.momentlog.server.document.application.DocumentException;
import team.momentlog.server.document.application.DocumentService;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Tag(name = "사고 정리 문서", description = "QR 공유 생성과 발급 문서 대조")
@RestController
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @Operation(summary = "QR 공유 생성",
            description = "차량 앱이 원본(canonical 문자열)과 진술을 올린다. 원본 해시가 봉인과 다르면 409, 봉인이 없으면 404, 원본 형식이 아니면 400. "
                    + "같은 기록에 진술·장소가 같은 유효한 공유가 있으면 그 공유를 200으로 돌려주고, 없으면 새로 만들어 201. "
                    + "응답의 token으로 QR 주소(/r/{token})를 만든다.")
    @PostMapping("/api/v1/records/{recordId}/documents")
    public ResponseEntity<DocumentResponse.ShareDetail> createShare(@PathVariable String recordId,
                                                                    @Valid @RequestBody DocumentRequest.CreateShare request) {
        DocumentService.CreatedShare created = documentService.createShare(
                recordId, request.body().getBytes(StandardCharsets.UTF_8), request.statementJson(), request.placeText());
        return ResponseEntity.status(created.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(DocumentResponse.ShareDetail.from(created));
    }

    @Operation(summary = "QR 공유 철회",
            description = "차에서 기록을 지웠을 때 차량 앱이 호출. 그 기록의 공유 링크를 모두 닫고(410) 원본·진술·PDF 파일을 지운다. "
                    + "X-Device-Id·X-Device-Token 헤더가 필요하고, 기록을 봉인한 기기만 할 수 있다(토큰 없음·다름 401, 다른 기기 403, 봉인 없음 404). "
                    + "이미 건넨 PDF의 원본 확인은 계속 된다. 여러 번 불러도 같은 결과(204).")
    @DeleteMapping("/api/v1/records/{recordId}/documents")
    public ResponseEntity<Void> revokeShares(@PathVariable String recordId,
                                             @RequestHeader(name = DeviceHeaders.DEVICE_ID, required = false) String deviceId,
                                             @RequestHeader(name = DeviceHeaders.DEVICE_TOKEN, required = false) String deviceToken) {
        documentService.revokeShares(recordId, deviceId, deviceToken);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "발급 문서 대조",
            description = "문서 번호(SG-YYYYMMDD-HHMMSS)와 제출된 PDF의 SHA-256으로 대조. MATCH / MISMATCH / NOT_FOUND. 확인할 때마다 이력을 남긴다.")
    @PostMapping("/api/v1/documents/{documentNo}/verify")
    public DocumentResponse.VerifyDetail verify(@PathVariable String documentNo,
                                                @Valid @RequestBody DocumentRequest.Verify request) {
        return new DocumentResponse.VerifyDetail(documentNo, documentService.verifyDocument(documentNo, request.sha256()));
    }

    @ExceptionHandler(DocumentException.SealNotFound.class)
    public ResponseEntity<Map<String, String>> sealNotFound(DocumentException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("errorCode", "SEAL_NOT_FOUND", "message", e.getMessage()));
    }

    @ExceptionHandler(DocumentException.InvalidBody.class)
    public ResponseEntity<Map<String, String>> invalidBody(DocumentException e) {
        return ResponseEntity.badRequest().body(Map.of("errorCode", "INVALID_BODY", "message", e.getMessage()));
    }

    @ExceptionHandler(DocumentException.NotOwner.class)
    public ResponseEntity<Map<String, String>> notOwner(DocumentException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("errorCode", "NOT_OWNER", "message", e.getMessage()));
    }

    @ExceptionHandler(DocumentException.BodyMismatch.class)
    public ResponseEntity<Map<String, String>> bodyMismatch(DocumentException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("errorCode", "BODY_MISMATCH", "message", e.getMessage()));
    }
}
