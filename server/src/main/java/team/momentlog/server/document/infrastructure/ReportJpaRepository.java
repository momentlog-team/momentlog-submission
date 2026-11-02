package team.momentlog.server.document.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import team.momentlog.server.document.model.Report;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ReportJpaRepository extends JpaRepository<Report, String> {

    Optional<Report> findByDocumentNo(String documentNo);

    List<Report> findByShareId(String shareId);

    Optional<Report> findFirstByShareId(String shareId);

    /** 같은 기록의 판본들, 최근 발급 순. */
    List<Report> findByRecordIdOrderByIssuedAtDesc(String recordId);

    List<Report> findByIssuedAtBefore(Instant cutoff);

    boolean existsByShareId(String shareId);

    boolean existsByRecordId(String recordId);
}
