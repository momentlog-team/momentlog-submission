package team.momentlog.server.document.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import team.momentlog.server.document.model.VerifyLog;

import java.time.Instant;

public interface VerifyLogJpaRepository extends JpaRepository<VerifyLog, Long> {

    @Modifying
    @Query("delete from VerifyLog v where v.verifiedAt < :cutoff")
    int deleteVerifiedBefore(@Param("cutoff") Instant cutoff);
}
