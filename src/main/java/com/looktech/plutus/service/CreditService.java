package com.looktech.plutus.service;

import com.looktech.plutus.domain.CreditLedger;
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
     * Reserve credits
     * @param userId User ID
     * @param amount Credit amount
     * @param transactionId Transaction ID
     * @return List of reserved credit ledgers
     */
    List<CreditLedger> reserveCredit(Long userId, BigDecimal amount, String transactionId);
    
    /**
     * Cancel credit reservation
     * @param transactionId Transaction ID
     */
    void cancelReservation(String transactionId);
    
    /**
     * Settle credit transaction
     * @param transactionId Transaction ID
     */
    void settleCredit(String transactionId);
    
    /**
     * Get user's available credit balance
     * @param userId User ID
     * @return Available balance
     */
    BigDecimal getAvailableBalance(Long userId);
    
    /**
     * Get user's transaction history
     * @param userId User ID
     * @param page Page number
     * @param size Page size
     * @return Paged transaction history
     */
    Page<CreditTransactionLog> getTransactionHistory(Long userId, int page, int size);
} 