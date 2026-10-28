package team.momentlog.server.document.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 발급한 사고 정리 PDF. 문서 번호(SG-YYYYMMDD-HHMMSS)로 찾고, 제출된 PDF의 해시를 [pdfSha256]과 대조한다(09 원본 대조 화면).
 * PDF 생성은 아직 없다. 발급 기능이 붙으면 여기에 한 건씩 남긴다.
 */
@Entity
@Table(name = "t_report")
public class Report {

    @Id
    @Column(name = "report_id", length = 36)
    private String reportId;

    @Column(name = "document_no", length = 32, nullable = false, unique = true)
    private String documentNo;

    @Column(name = "share_id", length = 36, nullable = false)
    private String shareId;

    @Column(name = "record_id", length = 64, nullable = false)
    private String recordId;

    @Column(name = "pdf_sha256", columnDefinition = "char(64)", nullable = false)
    private String pdfSha256;

    @Column(name = "file_name", length = 255, nullable = false)
    private String fileName;

    @Column(name = "byte_size", nullable = false)
    private int byteSize;

    @Column(name = "page_count", nullable = false)
    private int pageCount;

    @Column(name = "storage_key", length = 255, nullable = false)
    private String storageKey;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    protected Report() {
    }

    private Report(String reportId, String documentNo, String shareId, String recordId, String pdfSha256,
                   String fileName, int byteSize, int pageCount, String storageKey, Instant issuedAt) {
        this.reportId = reportId;
        this.documentNo = documentNo;
        this.shareId = shareId;
        this.recordId = recordId;
        this.pdfSha256 = pdfSha256;
        this.fileName = fileName;
        this.byteSize = byteSize;
        this.pageCount = pageCount;
        this.storageKey = storageKey;
        this.issuedAt = issuedAt;
    }

    public static Report issue(String reportId, String documentNo, String shareId, String recordId, String pdfSha256,
                               String fileName, int byteSize, int pageCount, String storageKey, Instant issuedAt) {
        return new Report(reportId, documentNo, shareId, recordId, pdfSha256.toLowerCase(),
                fileName, byteSize, pageCount, storageKey, issuedAt);
    }

    public boolean matches(String candidateSha256) {
        return candidateSha256 != null && pdfSha256.equalsIgnoreCase(candidateSha256);
    }

    public String getReportId() {
        return reportId;
    }

    public String getDocumentNo() {
        return documentNo;
    }

    public String getShareId() {
        return shareId;
    }

    public String getRecordId() {
        return recordId;
    }

    public String getPdfSha256() {
        return pdfSha256;
    }

    public String getFileName() {
        return fileName;
    }

    public int getByteSize() {
        return byteSize;
    }

    public int getPageCount() {
        return pageCount;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }
}
