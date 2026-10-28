package team.momentlog.server.document.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import team.momentlog.server.document.model.RecordShare;

import java.util.Optional;

public interface RecordShareJpaRepository extends JpaRepository<RecordShare, String> {

    Optional<RecordShare> findByTokenSha256(String tokenSha256);
}
