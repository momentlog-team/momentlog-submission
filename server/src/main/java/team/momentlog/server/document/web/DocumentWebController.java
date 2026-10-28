package team.momentlog.server.document.web;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
import team.momentlog.server.document.application.DocumentException;
import team.momentlog.server.document.application.ReportService;

import java.nio.charset.StandardCharsets;

/** QR로 여는 페이지. `/r/{token}` 문서 보기(08)·PDF 받기, `/v/{documentNo}` 원본 확인(09). */
@Hidden
@Controller
public class DocumentWebController {

    private static final MediaType HTML = new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8);

    private final ReportService reportService;

    public DocumentWebController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/r/{token}")
    @ResponseBody
    public ResponseEntity<String> share(@PathVariable String token) {
        return ResponseEntity.ok().contentType(HTML).body(DocumentPages.share(token, reportService.open(token)));
    }

    @GetMapping("/r/{token}/report.pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable String token) {
        ReportService.IssuedPdf issued = reportService.issue(token);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(issued.report().getFileName(), StandardCharsets.UTF_8).build().toString())
                .body(issued.bytes());
    }

    @GetMapping("/v/{documentNo}")
    @ResponseBody
    public ResponseEntity<String> verify(@PathVariable String documentNo) {
        return ResponseEntity.ok().contentType(HTML).body(DocumentPages.verify(documentNo, reportService.verifyTarget(documentNo)));
    }

    @ExceptionHandler(DocumentException.ShareNotFound.class)
    @ResponseBody
    public ResponseEntity<String> notFound(DocumentException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(HTML)
                .body(DocumentPages.error("문서를 찾을 수 없어요", "QR 주소가 맞는지 확인해 주세요."));
    }

    @ExceptionHandler(DocumentException.ShareExpired.class)
    @ResponseBody
    public ResponseEntity<String> expired(DocumentException e) {
        return ResponseEntity.status(HttpStatus.GONE).contentType(HTML)
                .body(DocumentPages.error("공유 기간이 지났어요", "차량에서 문서를 다시 보내 주세요."));
    }
}
