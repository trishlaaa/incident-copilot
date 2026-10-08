package com.trishla.copilot.repo;

import com.trishla.copilot.model.Incident;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface IncidentRepository extends JpaRepository<Incident, Long> {

    Optional<Incident> findFirstByFingerprintAndStatusNotAndLastSeenAfterOrderByLastSeenDesc(
            String fingerprint, String status, Instant after);

    List<Incident> findTop100ByOrderByLastSeenDesc();

    long countByStatusNot(String status);

    /** Writes only the AI columns, so it never clobbers counts updated by the consumer. */
    @Modifying
    @Transactional
    @Query("""
            update Incident i set i.aiRootCause = :rootCause, i.aiFix = :fix,
                   i.aiConfidence = :confidence, i.aiCitations = :citations,
                   i.aiSource = :source, i.aiAnalyzedAt = :analyzedAt
            where i.id = :id""")
    int updateAi(@Param("id") Long id, @Param("rootCause") String rootCause, @Param("fix") String fix,
                 @Param("confidence") String confidence, @Param("citations") String citations,
                 @Param("source") String source, @Param("analyzedAt") Instant analyzedAt);
}
