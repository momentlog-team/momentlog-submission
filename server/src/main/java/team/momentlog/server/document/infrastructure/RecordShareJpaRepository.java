package team.momentlog.server.document.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import team.momentlog.server.document.model.RecordShare;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RecordShareJpaRepository extends JpaRepository<RecordShare, String> {

    Optional<RecordShare> findByTokenSha256(String tokenSha256);

    List<RecordShare> findByExpiresAtLessThanEqualAndPurgedAtIsNull(Instant now);

    List<RecordShare> findByCreatedAtBefore(Instant cutoff);

    boolean existsByRecordId(String recordId);
}
