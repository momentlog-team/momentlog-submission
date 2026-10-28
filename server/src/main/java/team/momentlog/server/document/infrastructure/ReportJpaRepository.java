package team.momentlog.server.document.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import team.momentlog.server.document.model.Report;

import java.util.Optional;

public interface ReportJpaRepository extends JpaRepository<Report, String> {

    Optional<Report> findByDocumentNo(String documentNo);

    Optional<Report> findFirstByRecordId(String recordId);
}
