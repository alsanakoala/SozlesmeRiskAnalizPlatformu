package com.riskguard.repo;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.riskguard.entity.ClauseEntity;

public interface ClauseRepository extends JpaRepository<ClauseEntity, UUID> {
    List<ClauseEntity> findByDocumentIdOrderByIdxAsc(UUID documentId);
}
