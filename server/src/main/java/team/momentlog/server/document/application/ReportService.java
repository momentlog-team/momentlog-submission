package team.momentlog.server.document.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.momentlog.core.RecordHasher;
import team.momentlog.server.document.infrastructure.RecordShareJpaRepository;
import team.momentlog.server.document.infrastructure.ReportJpaRepository;
import team.momentlog.server.document.model.RecordShare;
import team.momentlog.server.document.model.Report;
import team.momentlog.server.document.report.ReportFacts;
import team.momentlog.server.document.report.ReportPdfRenderer;
import team.momentlog.server.document.report.Statement;
import team.momentlog.server.record.infrastructure.RecordSealJpaRepository;
import team.momentlog.server.record.model.RecordSeal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * QR 공유로 여는 문서. 같은 기록의 PDF는 한 번만 만들어 저장하고 이후에는 같은 파일을 준다
 * (다시 만들면 해시가 달라져 09 원본 대조가 깨지기 때문).
 */
@Service
public class ReportService {

    private final RecordShareJpaRepository recordShareJpaRepository;
    private final RecordSealJpaRepository recordSealJpaRepository;
    private final ReportJpaRepository reportJpaRepository;
    private final ReportPdfRenderer renderer;
    private final Clock clock;
    private final Path reportDir;
    private final String publicBaseUrl;

    public ReportService(RecordShareJpaRepository recordShareJpaRepository,
                         RecordSealJpaRepository recordSealJpaRepository,
                         ReportJpaRepository reportJpaRepository,
                         ReportPdfRenderer renderer,
                         Clock clock,
                         @Value("${momentlog.report.dir:./data/reports}") String reportDir,
                         @Value("${momentlog.public-base-url:http://localhost:8090}") String publicBaseUrl) {
        this.recordShareJpaRepository = recordShareJpaRepository;
        this.recordSealJpaRepository = recordSealJpaRepository;
        this.reportJpaRepository = reportJpaRepository;
        this.renderer = renderer;
        this.clock = clock;
        this.reportDir = Path.of(reportDir);
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    /** QR 페이지에 보여 줄 내용. 처음 연 시각을 남긴다. */
    @Transactional
    public SharedDocument open(String token) {
        RecordShare share = findShare(token);
        share.open(Instant.now(clock));
        RecordSeal seal = recordSealJpaRepository.findById(share.getRecordId()).orElseThrow();
        Report report = reportJpaRepository.findFirstByShareId(share.getShareId()).orElse(null);
        return new SharedDocument(share, seal, facts(share), Statement.parse(share.getStatementJson()), report);
    }

    /**
     * PDF를 돌려준다. 이 공유(진술 판본)의 PDF가 없으면 만들어 저장하고 t_report에 남긴다.
     * 같은 공유면 같은 파일을 다시 준다(해시 고정). 진술이 바뀐 새 공유는 새 문서 번호로 따로 발급한다.
     */
    @Transactional
    public IssuedPdf issue(String token) {
        RecordShare share = findShare(token);
        Report existing = reportJpaRepository.findFirstByShareId(share.getShareId()).orElse(null);
        if (existing != null) {
            return new IssuedPdf(existing, read(existing.getStorageKey()));
        }

        RecordSeal seal = recordSealJpaRepository.findById(share.getRecordId()).orElseThrow();
        ReportFacts facts = facts(share);
        String documentNo = documentNo(facts);
        Instant now = Instant.now(clock);
        byte[] pdf = renderer.render(new ReportPdfRenderer.Input(
                facts, Statement.parse(share.getStatementJson()), share.getPlaceText(), documentNo, now,
                seal.getSealedAt(), seal.getSha256(), publicBaseUrl + "/v/" + documentNo));

        String reportId = UUID.randomUUID().toString();
        String storageKey = reportId + ".pdf";
        write(storageKey, pdf);
        Report report = reportJpaRepository.save(Report.issue(reportId, documentNo, share.getShareId(), share.getRecordId(),
                DocumentService.sha256(pdf), "순간기록_" + facts.stamp().replace('-', '_') + ".pdf",
                pdf.length, 1, storageKey, now, facts.triggerAt().toInstant(), facts.lead(), share.getPlaceText()));
        return new IssuedPdf(report, pdf);
    }

    /**
     * 09 원본 대조 페이지가 보여 줄 문서. 없으면 null.
     * 공유 본문은 30일 뒤 지워지므로 t_report에 복사해 둔 값과 봉인만 쓴다.
     * 같은 기록에 더 나중에 발급한 판본(진술 수정)이 있으면 그 문서 번호를 함께 준다.
     */
    @Transactional(readOnly = true)
    public VerifyTarget verifyTarget(String documentNo) {
        return reportJpaRepository.findByDocumentNo(documentNo)
                .map(report -> {
                    String newer = reportJpaRepository.findByRecordIdOrderByIssuedAtDesc(report.getRecordId()).stream()
                            .filter(other -> other.getIssuedAt().isAfter(report.getIssuedAt()))
                            .map(Report::getDocumentNo)
                            .findFirst().orElse(null);
                    return new VerifyTarget(report, recordSealJpaRepository.findById(report.getRecordId()).orElseThrow(), newer);
                })
                .orElse(null);
    }

    public record SharedDocument(RecordShare share, RecordSeal seal, ReportFacts facts, Statement statement, Report report) {
    }

    public record IssuedPdf(Report report, byte[] bytes) {
    }

    /** [newerDocumentNo]: 같은 기록의 가장 최근 판본 문서 번호. 이 문서가 최신이면 null. */
    public record VerifyTarget(Report report, RecordSeal seal, String newerDocumentNo) {
    }

    private RecordShare findShare(String token) {
        RecordShare share = recordShareJpaRepository
                .findByTokenSha256(DocumentService.sha256(token.getBytes(StandardCharsets.UTF_8)))
                .orElseThrow(DocumentException.ShareNotFound::new);
        if (share.isExpired(Instant.now(clock))) {
            throw new DocumentException.ShareExpired();
        }
        return share;
    }

    private static ReportFacts facts(RecordShare share) {
        return ReportFacts.of(RecordHasher.INSTANCE.parse(new String(share.getBody(), StandardCharsets.UTF_8)));
    }

    /** SG-YYYYMMDD-HHMMSS(기준 시각). 다른 기록과 같은 초에 겹치면 뒤에 번호를 붙인다. */
    private String documentNo(ReportFacts facts) {
        String base = "SG-" + facts.stamp();
        String candidate = base;
        for (int i = 2; reportJpaRepository.findByDocumentNo(candidate).isPresent(); i++) {
            candidate = base + "-" + i;
        }
        return candidate;
    }

    private void write(String storageKey, byte[] bytes) {
        try {
            Files.createDirectories(reportDir);
            Files.write(reportDir.resolve(storageKey), bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 보관 기간이 지난 PDF 파일을 지운다. 이미 없으면 그냥 넘어간다. */
    public void deleteFile(String storageKey) {
        try {
            Files.deleteIfExists(reportDir.resolve(storageKey));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private byte[] read(String storageKey) {
        try {
            return Files.readAllBytes(reportDir.resolve(storageKey));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
