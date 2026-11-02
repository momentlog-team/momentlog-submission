package team.momentlog.server.record.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import team.momentlog.server.record.model.RecordSeal;

import java.time.Instant;
import java.util.List;

public interface RecordSealJpaRepository extends JpaRepository<RecordSeal, String> {

    List<RecordSeal> findBySealedAtBefore(Instant cutoff);
}
