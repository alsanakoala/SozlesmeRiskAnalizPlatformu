package com.riskguard.repo;

import com.riskguard.entity.DocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public interface DocumentRepository extends JpaRepository<DocumentEntity, UUID> {

    // Mevcut metodların varsa onları KORU. Örneğin:
    // Optional<DocumentEntity> findById(UUID id);
    // vs. Bunları silmiyoruz.

    /**
     * Aynı version_group_id değerine sahip tüm dokümanları
     * uploaded_at zamanına göre artan sırada (eski -> yeni) getirir.
     *
     * Bu, versiyon geçmişini kronolojik olarak incelemek için kullanılıyor.
     */
    List<DocumentEntity> findByVersionGroupIdOrderByUploadedAtAsc(UUID versionGroupId);
}
