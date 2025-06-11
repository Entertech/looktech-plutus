package com.looktech.plutus.repository;

import com.looktech.plutus.domain.CreditTransactionLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CreditTransactionLogRepository extends JpaRepository<CreditTransactionLog, Long> {
    
    Optional<CreditTransactionLog> findByTransactionId(String transactionId);
    
    Page<CreditTransactionLog> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    
    Optional<CreditTransactionLog> findBySourceId(String sourceId);
} 