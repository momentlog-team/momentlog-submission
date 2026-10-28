package team.momentlog.server.document.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.momentlog.core.RecordHasher;
import team.momentlog.server.document.infrastructure.RecordShareJpaRepository;
import team.momentlog.server.document.infrastructure.ReportJpaRepository;
import team.momentlog.server.document.infrastructure.VerifyLogJpaRepository;
import team.momentlog.server.document.model.RecordShare;
import team.momentlog.server.document.model.VerifyLog;
import team.momentlog.server.record.application.VerifyResult;
import team.momentlog.server.record.infrastructure.RecordSealJpaRepository;
import team.momentlog.server.record.model.RecordSeal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class DocumentService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RecordSealJpaRepository recordSealJpaRepository;
    private final RecordShareJpaRepository recordShareJpaRepository;
    private final ReportJpaRepository reportJpaRepository;
    private final VerifyLogJpaRepository verifyLogJpaRepository;
    private final Clock clock;
    private final Duration shareTtl;

    public DocumentService(RecordSealJpaRepository recordSealJpaRepository,
                           RecordShareJpaRepository recordShareJpaRepository,
                           ReportJpaRepository reportJpaRepository,
                           VerifyLogJpaRepository verifyLogJpaRepository,
                           Clock clock,
                           @Value("${momentlog.share.ttl:P30D}") Duration shareTtl) {
        this.recordSealJpaRepository = recordSealJpaRepository;
        this.recordShareJpaRepository = recordShareJpaRepository;
        this.reportJpaRepository = reportJpaRepository;
        this.verifyLogJpaRepository = verifyLogJpaRepository;
        this.clock = clock;
        this.shareTtl = shareTtl;
    }

    /**
     * 원본 바이트로 QR 공유를 만든다. 바이트의 SHA-256이 봉인된 해시와 같아야 하고(고친 기록으로는 문서를 만들 수 없다),
     * 문서를 만들 수 있게 core 원본 형식으로 읽혀야 한다.
     * 토큰 원문은 이 응답에서 한 번만 돌려주고, DB에는 해시만 남긴다.
     */
    @Transactional
    public CreatedShare createShare(String recordId, byte[] body, String statementJson, String placeText) {
        RecordSeal seal = recordSealJpaRepository.findById(recordId)
                .orElseThrow(() -> new DocumentException.SealNotFound(recordId));
        if (!seal.matches(sha256(body))) {
            throw new DocumentException.BodyMismatch(recordId);
        }
        try {
            RecordHasher.INSTANCE.parse(new String(body, StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            throw new DocumentException.InvalidBody(e.getMessage());
        }
        Instant now = Instant.now(clock);
        String token = newToken();
        RecordShare share = recordShareJpaRepository.save(RecordShare.create(
                UUID.randomUUID().toString(), sha256(token.getBytes(StandardCharsets.UTF_8)), recordId, body,
                statementJson, placeText, now, now.plus(shareTtl)));
        return new CreatedShare(share, token);
    }

    /** 문서 번호로 발급 PDF를 찾아 제출된 해시와 대조하고, 결과와 무관하게 이력을 남긴다. */
    @Transactional
    public VerifyResult.Status verifyDocument(String documentNo, String submittedSha256) {
        VerifyResult.Status status = reportJpaRepository.findByDocumentNo(documentNo)
                .map(report -> report.matches(submittedSha256) ? VerifyResult.Status.MATCH : VerifyResult.Status.MISMATCH)
                .orElse(VerifyResult.Status.NOT_FOUND);
        verifyLogJpaRepository.save(VerifyLog.of(documentNo, submittedSha256, status, Instant.now(clock)));
        return status;
    }

    public record CreatedShare(RecordShare share, String token) {
    }

    private static String newToken() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
