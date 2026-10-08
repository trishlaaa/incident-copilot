package com.trishla.copilot.repo;

import com.trishla.copilot.model.LogEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LogEventRepository extends JpaRepository<LogEventEntity, Long> {

    List<LogEventEntity> findTop10ByIncidentIdOrderByTimestampDesc(Long incidentId);
}
