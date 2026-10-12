package backend.logs;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RequestLogRepository extends JpaRepository<RequestLogEntity, Long> {

    List<RequestLogEntity> findByCreatedAtAfterOrderByCreatedAtDesc(Instant after, Pageable pageable);

    List<RequestLogEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByStatusBetween(int from, int to);

    long countByStatus(int status);

    @Query("select avg(r.latencyMs) from RequestLogEntity r")
    Double averageLatency();
}
