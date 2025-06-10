package com.looktech.plutus.repository;

import com.looktech.plutus.domain.CreditLedger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;

@Repository
public interface CreditLedgerRepository extends JpaRepository<CreditLedger, Long> {
    
    List<CreditLedger> findByUserIdAndStatusAndExpiresAtAfterOrderByExpiresAtAsc(
            Long userId, 
            CreditLedger.CreditStatus status, 
            LocalDateTime now);
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CreditLedger c WHERE c.id = :id")
    Optional<CreditLedger> findByIdWithLock(@Param("id") Long id);
    
    @Query("SELECT SUM(c.remainingAmount) FROM CreditLedger c " +
           "WHERE c.userId = :userId AND c.status = :status AND c.expiresAt > :now")
    Optional<BigDecimal> sumRemainingAmountByUserIdAndStatusAndNotExpired(
            @Param("userId") Long userId,
            @Param("status") CreditLedger.CreditStatus status,
            @Param("now") LocalDateTime now);
} 