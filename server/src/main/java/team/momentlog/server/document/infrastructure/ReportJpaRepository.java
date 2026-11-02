package team.momentlog.server.document.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import team.momentlog.server.document.model.Report;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ReportJpaRepository extends JpaRepository<Report, String> {

    Optional<Report> findByDocumentNo(String documentNo);

    Optional<Report> findFirstByRecordId(String recordId);

    List<Report> findByShareId(String shareId);

    List<Report> findByIssuedAtBefore(Instant cutoff);

    boolean existsByShareId(String shareId);

    boolean existsByRecordId(String recordId);
}
