package team.momentlog.server.record.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import team.momentlog.server.record.model.RecordSeal;

public interface RecordSealJpaRepository extends JpaRepository<RecordSeal, String> {
}
