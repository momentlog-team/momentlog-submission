package team.momentlog.server.retention.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.momentlog.server.document.application.ReportService;
import team.momentlog.server.document.infrastructure.RecordShareJpaRepository;
import team.momentlog.server.document.infrastructure.ReportJpaRepository;
import team.momentlog.server.document.infrastructure.VerifyLogJpaRepository;
import team.momentlog.server.document.model.RecordShare;
import team.momentlog.server.document.model.Report;
import team.momentlog.server.record.infrastructure.RecordSealJpaRepository;
import team.momentlog.server.record.model.RecordSeal;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * 서버 보관 기간(2026-11-02 결정).
 * <ul>
 *   <li>QR 공유의 원본 본문·진술·장소와 PDF 파일: 공유 만료(momentlog.share.ttl, 30일) 뒤 삭제</li>
 *   <li>발급 문서 지문(t_report)과 원본 봉인(t_record_seal): 3년. 원본 확인(09)이 이 기간 동안 동작한다</li>
 *   <li>원본 확인 이력(t_verify_log): 90일</li>
 * </ul>
 * 봉인은 그 기록의 공유·문서가 모두 지워진 뒤에만 지운다(외래키).
 */
@Service
public class RetentionService {

    private static final Logger log = LoggerFactory.getLogger(RetentionService.class);

    private final RecordShareJpaRepository recordShareJpaRepository;
    private final ReportJpaRepository reportJpaRepository;
    private final VerifyLogJpaRepository verifyLogJpaRepository;
    private final RecordSealJpaRepository recordSealJpaRepository;
    private final ReportService reportService;
    private final Clock clock;
    private final Duration proofTtl;
    private final Duration verifyLogTtl;

    public RetentionService(RecordShareJpaRepository recordShareJpaRepository,
                            ReportJpaRepository reportJpaRepository,
                            VerifyLogJpaRepository verifyLogJpaRepository,
                            RecordSealJpaRepository recordSealJpaRepository,
                            ReportService reportService,
                            Clock clock,
                            @Value("${momentlog.retention.proof-ttl:P1095D}") Duration proofTtl,
                            @Value("${momentlog.retention.verify-log-ttl:P90D}") Duration verifyLogTtl) {
        this.recordShareJpaRepository = recordShareJpaRepository;
        this.reportJpaRepository = reportJpaRepository;
        this.verifyLogJpaRepository = verifyLogJpaRepository;
        this.recordSealJpaRepository = recordSealJpaRepository;
        this.reportService = reportService;
        this.clock = clock;
        this.proofTtl = proofTtl;
        this.verifyLogTtl = verifyLogTtl;
    }

    /** 매일 새벽(한국 시각)에 돈다. */
    @Scheduled(cron = "${momentlog.retention.cron:0 30 4 * * *}", zone = "Asia/Seoul")
    public void scheduled() {
        Result result = purge();
        log.info("보관 기간 정리: {}", result);
    }

    @Transactional
    public Result purge() {
        Instant now = Instant.now(clock);
        Instant proofCutoff = now.minus(proofTtl);

        int shares = 0;
        for (RecordShare share : recordShareJpaRepository.findByExpiresAtLessThanEqualAndPurgedAtIsNull(now)) {
            reportJpaRepository.findByShareId(share.getShareId()).forEach(report -> reportService.deleteFile(report.getStorageKey()));
            share.purge(now);
            shares++;
        }

        int verifyLogs = verifyLogJpaRepository.deleteVerifiedBefore(now.minus(verifyLogTtl));

        int reports = 0;
        for (Report report : reportJpaRepository.findByIssuedAtBefore(proofCutoff)) {
            reportService.deleteFile(report.getStorageKey());
            reportJpaRepository.delete(report);
            reports++;
        }
        reportJpaRepository.flush();

        int shareRows = 0;
        for (RecordShare share : recordShareJpaRepository.findByCreatedAtBefore(proofCutoff)) {
            if (!reportJpaRepository.existsByShareId(share.getShareId())) {
                recordShareJpaRepository.delete(share);
                shareRows++;
            }
        }
        recordShareJpaRepository.flush();

        int seals = 0;
        for (RecordSeal seal : recordSealJpaRepository.findBySealedAtBefore(proofCutoff)) {
            if (!reportJpaRepository.existsByRecordId(seal.getRecordId())
                    && !recordShareJpaRepository.existsByRecordId(seal.getRecordId())) {
                recordSealJpaRepository.delete(seal);
                seals++;
            }
        }
        return new Result(shares, verifyLogs, reports, shareRows, seals);
    }

    /**
     * @param purgedShares 본문·진술을 비운 공유
     * @param verifyLogs   지운 확인 이력
     * @param reports      지운 발급 문서 행
     * @param shareRows    지운 공유 행
     * @param seals        지운 봉인
     */
    public record Result(int purgedShares, int verifyLogs, int reports, int shareRows, int seals) {
    }
}
