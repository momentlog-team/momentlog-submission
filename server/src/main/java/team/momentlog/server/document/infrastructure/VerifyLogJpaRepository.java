package team.momentlog.server.document.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import team.momentlog.server.document.model.VerifyLog;

public interface VerifyLogJpaRepository extends JpaRepository<VerifyLog, Long> {
}
