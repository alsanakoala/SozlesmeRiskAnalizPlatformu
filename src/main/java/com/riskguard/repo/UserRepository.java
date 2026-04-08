package com.riskguard.repo;

import com.riskguard.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, UUID> {
    
    // Kullanıcı giriş yaparken e-postasından bulmak için kullanacağız
    Optional<UserEntity> findByEmail(String email);
    
    // Kayıt olurken e-posta daha önce alınmış mı diye kontrol edeceğiz
    boolean existsByEmail(String email);
}