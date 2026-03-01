package com.riskguard.repo;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.riskguard.entity.RiskFindingEntity;

public interface RiskFindingRepository extends JpaRepository<RiskFindingEntity, UUID> {
    List<RiskFindingEntity> findByDocumentId(UUID documentId);
}
