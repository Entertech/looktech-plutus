package com.looktech.plutus.service;

import com.looktech.plutus.domain.CreditTransactionLog;
import com.looktech.plutus.domain.CreateSessionResponse;
import com.looktech.plutus.dto.BatchCreditGrantRequest;
import com.looktech.plutus.dto.BatchCreditGrantResponse;
import com.looktech.plutus.enums.SourceType;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface CreditService {
    
    /**
     * Grant credits to user
     * @param userId User ID
     * @param amount Credit amount
     * @param sourceType Source type
     * @param sourceId Source ID
     * @param expiresAt Expiration time
     * @param idempotencyId Unique ID for idempotency
     * @return Transaction log
     */
    CreditTransactionLog grantCredit(Long userId, BigDecimal amount, SourceType sourceType, String sourceId, LocalDateTime expiresAt, String idempotencyId);
    
    /**
     * Batch grant credits to multiple users
     * @param items List of credit grant items
     * @return Batch grant response containing success and failure results
     */
    BatchCreditGrantResponse batchGrantCredit(List<BatchCreditGrantRequest.CreditGrantItem> items);
    
    /**
     * Start a new credit session and reserve credits
     * @param userId User ID
     * @param maxAmount Maximum amount to reserve
     * @param idempotencyId Unique ID for idempotency
     * @return Session ID
     */
    CreateSessionResponse startSession(Long userId, BigDecimal maxAmount, String idempotencyId);
    
    /**
     * Settle a credit session with final amount
     * @param sessionId Session ID
     * @param finalAmount Final amount to consume
     * @return Transaction log
     */
    CreditTransactionLog settleSession(String sessionId, BigDecimal finalAmount);
    
    /**
     * Cancel a credit session and release reserved credits
     * @param sessionId Session ID
     */
    void cancelSession(String sessionId);
    
    /**
     * Deduct credits synchronously
     * @param userId User ID
     * @param amount Amount to deduct
     * @param sourceType Source type of the deduction
     * @param sourceId Source ID of the deduction
     * @param idempotencyId Unique ID for idempotency
     * @return Transaction log
     */
    CreditTransactionLog deductCredit(Long userId, BigDecimal amount, SourceType sourceType, String sourceId, String idempotencyId);
    
    /**
     * Get available balance for user
     * @param userId User ID
     * @return Available balance
     */
    BigDecimal getAvailableBalance(Long userId);
    
    /**
     * Get transaction history for user
     * @param userId User ID
     * @param page Page number
     * @param size Page size
     * @return Paged transaction history
     */
    Page<CreditTransactionLog> getTransactionHistory(Long userId, int page, int size);
} 