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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Arrays;
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
    private final byte[] tokenKey;

    public DocumentService(RecordSealJpaRepository recordSealJpaRepository,
                           RecordShareJpaRepository recordShareJpaRepository,
                           ReportJpaRepository reportJpaRepository,
                           VerifyLogJpaRepository verifyLogJpaRepository,
                           Clock clock,
                           @Value("${momentlog.share.ttl:P30D}") Duration shareTtl,
                           @Value("${momentlog.share.token-secret:}") String tokenSecret) {
        this.recordSealJpaRepository = recordSealJpaRepository;
        this.recordShareJpaRepository = recordShareJpaRepository;
        this.reportJpaRepository = reportJpaRepository;
        this.verifyLogJpaRepository = verifyLogJpaRepository;
        this.clock = clock;
        this.shareTtl = shareTtl;
        this.tokenKey = tokenSecret.isBlank() ? randomKey() : tokenSecret.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 원본 바이트로 QR 공유를 만든다. 바이트의 SHA-256이 봉인된 해시와 같아야 하고(고친 기록으로는 문서를 만들 수 없다),
     * 문서를 만들 수 있게 core 원본 형식으로 읽혀야 한다.
     * <p>
     * 같은 기록에 아직 유효하고 진술·장소까지 같은 공유가 있으면 새로 만들지 않고 그 공유를 돌려준다(QR을 여러 번 열어도 링크 하나).
     * 진술이 바뀌면 새 공유(새 판본)를 만들고, 앞 판본 링크와 PDF는 만료까지 그대로 둔다.
     * 토큰은 공유 ID의 HMAC이라 다시 만들 수 있고, DB에는 해시만 남긴다.
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
        for (RecordShare active : recordShareJpaRepository.findByRecordIdAndExpiresAtAfterAndPurgedAtIsNull(recordId, now)) {
            if (!active.hasSameContent(statementJson, placeText)) continue;
            String token = tokenFor(active.getShareId());
            // 서버 재시작으로 토큰 키가 바뀌었거나 예전 방식(난수) 토큰이면 다시 만들 수 없으니 새로 만든다.
            if (active.matchesToken(sha256(token.getBytes(StandardCharsets.UTF_8)))) {
                return new CreatedShare(active, token, false);
            }
        }
        String shareId = UUID.randomUUID().toString();
        String token = tokenFor(shareId);
        RecordShare share = recordShareJpaRepository.save(RecordShare.create(
                shareId, sha256(token.getBytes(StandardCharsets.UTF_8)), recordId, body,
                statementJson, placeText, now, now.plus(shareTtl)));
        return new CreatedShare(share, token, true);
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

    /** [created]가 false면 같은 내용의 기존 공유를 돌려준 것. */
    public record CreatedShare(RecordShare share, String token, boolean created) {
    }

    /** 공유 ID의 HMAC-SHA256 앞 24바이트(URL용 32자). */
    private String tokenFor(String shareId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(tokenKey, "HmacSHA256"));
            byte[] digest = mac.doFinal(shareId.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(Arrays.copyOf(digest, 24));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 설정이 없으면 실행마다 새 키. 재시작 뒤에는 기존 공유를 재사용하지 못하고 새로 만들 뿐, 기존 링크는 계속 열린다. */
    private static byte[] randomKey() {
        byte[] key = new byte[32];
        RANDOM.nextBytes(key);
        return key;
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
