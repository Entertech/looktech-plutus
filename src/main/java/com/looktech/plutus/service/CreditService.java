package com.looktech.plutus.service;

import com.looktech.plutus.domain.CreditTransactionLog;
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
     * @return Transaction log
     */
    CreditTransactionLog grantCredit(Long userId, BigDecimal amount, String sourceType, String sourceId, LocalDateTime expiresAt);
    
    /**
     * Start a new credit session and reserve credits
     * @param userId User ID
     * @param maxAmount Maximum amount to reserve
     * @param requestId Unique request ID for idempotency
     * @return Session ID
     */
    String startSession(Long userId, BigDecimal maxAmount, String requestId);
    
    /**
     * Settle a credit session with final amount
     * @param sessionId Session ID
     * @param finalAmount Final amount to consume
     * @param requestId Unique request ID for idempotency
     * @return Transaction log
     */
    CreditTransactionLog settleSession(String sessionId, BigDecimal finalAmount, String requestId);
    
    /**
     * Cancel a credit session and release reserved credits
     * @param sessionId Session ID
     * @param requestId Unique request ID for idempotency
     */
    void cancelSession(String sessionId, String requestId);
    
    /**
     * Deduct credits synchronously
     * @param userId User ID
     * @param amount Amount to deduct
     * @param sourceType Source type of the deduction
     * @param sourceId Source ID of the deduction
     * @param requestId Unique request ID for idempotency
     * @return Transaction log
     */
    CreditTransactionLog deductCredit(Long userId, BigDecimal amount, String sourceType, String sourceId, String requestId);
    
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