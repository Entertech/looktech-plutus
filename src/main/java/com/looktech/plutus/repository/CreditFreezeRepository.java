package com.looktech.plutus.repository;

import com.looktech.plutus.domain.CreditFreeze;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

public interface CreditFreezeRepository extends JpaRepository<CreditFreeze, Long> {
    Optional<CreditFreeze> findBySessionId(String sessionId);
    
    @Query("SELECT COALESCE(SUM(f.amount), 0) FROM CreditFreeze f " +
           "WHERE f.userId = :userId AND f.status = :status AND f.expiresAt > :now")
    Optional<BigDecimal> sumAmountByUserIdAndStatusAndNotExpired(
        @Param("userId") Long userId,
        @Param("status") CreditFreeze.FreezeStatus status,
        @Param("now") LocalDateTime now);
} 