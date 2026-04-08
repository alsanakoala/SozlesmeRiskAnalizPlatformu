package com.riskguard.repo;

import com.riskguard.entity.DocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<DocumentEntity, UUID> {

    /**
     * Aynı version_group_id değerine sahip tüm dokümanları
     * uploaded_at zamanına göre artan sırada (eski -> yeni) getirir.
     * Bu, versiyon geçmişini kronolojik olarak incelemek için kullanılıyor.
     */
    List<DocumentEntity> findByVersionGroupIdOrderByUploadedAtAsc(UUID versionGroupId);

    /**
     * 🔥 YENİ: Giriş yapan kullanıcının belgelerini (Geçmiş panelinde göstermek için)
     * en son yüklenenden eskiye doğru getirir.
     */
    List<DocumentEntity> findByOwnerEmailOrderByUploadedAtDesc(String ownerEmail);
}