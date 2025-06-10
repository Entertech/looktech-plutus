package com.looktech.plutus.repository;

import com.looktech.plutus.domain.UserCreditSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;
import java.util.Optional;

@Repository
public interface UserCreditSummaryRepository extends JpaRepository<UserCreditSummary, Long> {
    
    Optional<UserCreditSummary> findByUserId(Long userId);
    
    @Lock(LockModeType.OPTIMISTIC)
    @Query("SELECT u FROM UserCreditSummary u WHERE u.userId = :userId")
    Optional<UserCreditSummary> findByUserIdWithLock(@Param("userId") Long userId);
} 