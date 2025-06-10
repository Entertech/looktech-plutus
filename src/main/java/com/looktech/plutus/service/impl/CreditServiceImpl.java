package com.looktech.plutus.service.impl;

import com.looktech.plutus.domain.CreditLedger;
import com.looktech.plutus.domain.CreditTransactionLog;
import com.looktech.plutus.domain.UserCreditSummary;
import com.looktech.plutus.exception.CreditException;
import com.looktech.plutus.repository.CreditLedgerRepository;
import com.looktech.plutus.repository.CreditTransactionLogRepository;
import com.looktech.plutus.repository.UserCreditSummaryRepository;
import com.looktech.plutus.service.CreditService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreditServiceImpl implements CreditService {

    private final UserCreditSummaryRepository userCreditSummaryRepository;
    private final CreditLedgerRepository creditLedgerRepository;
    private final CreditTransactionLogRepository transactionLogRepository;

    @Override
    @Transactional
    @CacheEvict(value = "userBalance", key = "#userId")
    public CreditTransactionLog grantCredit(Long userId, BigDecimal amount, String sourceType, String sourceId, LocalDateTime expiresAt) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new CreditException("INVALID_AMOUNT", "Credit amount must be positive");
        }

        // Create credit ledger entry
        CreditLedger ledger = new CreditLedger();
        ledger.setUserId(userId);
        ledger.setRemainingAmount(amount);
        ledger.setStatus(CreditLedger.CreditStatus.ACTIVE);
        ledger.setSourceType(sourceType);
        ledger.setSourceId(sourceId);
        ledger.setExpiresAt(expiresAt);
        creditLedgerRepository.save(ledger);

        // Update user credit summary
        UserCreditSummary summary = userCreditSummaryRepository.findByUserId(userId)
                .orElseGet(() -> {
                    UserCreditSummary newSummary = new UserCreditSummary();
                    newSummary.setUserId(userId);
                    newSummary.setTotalBalance(BigDecimal.ZERO);
                    return newSummary;
                });
        summary.setTotalBalance(summary.getTotalBalance().add(amount));
        userCreditSummaryRepository.save(summary);

        // Record transaction log
        CreditTransactionLog log = new CreditTransactionLog();
        log.setUserId(userId);
        log.setTransactionId(UUID.randomUUID().toString());
        log.setType(CreditTransactionLog.TransactionType.GRANT);
        log.setAmount(amount);
        log.setSourceType(sourceType);
        log.setSourceId(sourceId);
        return transactionLogRepository.save(log);
    }

    @Override
    @Transactional
    @CacheEvict(value = "userBalance", key = "#userId")
    public List<CreditLedger> reserveCredit(Long userId, BigDecimal amount, String transactionId) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new CreditException("INVALID_AMOUNT", "Credit amount must be positive");
        }

        // Check available balance
        BigDecimal availableBalance = getAvailableBalance(userId);
        if (availableBalance.compareTo(amount) < 0) {
            throw new CreditException("INSUFFICIENT_BALANCE", "Insufficient credit balance");
        }

        // Get available credit ledgers ordered by expiration date
        List<CreditLedger> availableLedgers = creditLedgerRepository
                .findByUserIdAndStatusAndExpiresAtAfterOrderByExpiresAtAsc(
                        userId,
                        CreditLedger.CreditStatus.ACTIVE,
                        LocalDateTime.now());

        // Reserve credits
        BigDecimal remainingAmount = amount;
        for (CreditLedger ledger : availableLedgers) {
            if (remainingAmount.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }

            BigDecimal reserveAmount = ledger.getRemainingAmount().min(remainingAmount);
            ledger.setRemainingAmount(ledger.getRemainingAmount().subtract(reserveAmount));
            ledger.setStatus(CreditLedger.CreditStatus.RESERVED);
            creditLedgerRepository.save(ledger);

            remainingAmount = remainingAmount.subtract(reserveAmount);
        }

        // Record reservation transaction
        CreditTransactionLog log = new CreditTransactionLog();
        log.setUserId(userId);
        log.setTransactionId(transactionId);
        log.setType(CreditTransactionLog.TransactionType.RESERVE);
        log.setAmount(amount);
        transactionLogRepository.save(log);

        return availableLedgers;
    }

    @Override
    @Transactional
    @CacheEvict(value = "userBalance", key = "#result.userId")
    public void cancelReservation(String transactionId) {
        CreditTransactionLog log = transactionLogRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new CreditException("TRANSACTION_NOT_FOUND", "Transaction not found"));

        // Restore reserved credits
        List<CreditLedger> reservedLedgers = creditLedgerRepository
                .findByUserIdAndStatusAndExpiresAtAfterOrderByExpiresAtAsc(
                        log.getUserId(),
                        CreditLedger.CreditStatus.RESERVED,
                        LocalDateTime.now());

        for (CreditLedger ledger : reservedLedgers) {
            ledger.setStatus(CreditLedger.CreditStatus.ACTIVE);
            creditLedgerRepository.save(ledger);
        }

        // Record cancellation transaction
        CreditTransactionLog cancelLog = new CreditTransactionLog();
        cancelLog.setUserId(log.getUserId());
        cancelLog.setTransactionId(UUID.randomUUID().toString());
        cancelLog.setType(CreditTransactionLog.TransactionType.CANCEL);
        cancelLog.setAmount(log.getAmount());
        transactionLogRepository.save(cancelLog);
    }

    @Override
    @Transactional
    @CacheEvict(value = "userBalance", key = "#result.userId")
    public void settleCredit(String transactionId) {
        CreditTransactionLog log = transactionLogRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new CreditException("TRANSACTION_NOT_FOUND", "Transaction not found"));

        // Update reserved credits status to consumed
        List<CreditLedger> reservedLedgers = creditLedgerRepository
                .findByUserIdAndStatusAndExpiresAtAfterOrderByExpiresAtAsc(
                        log.getUserId(),
                        CreditLedger.CreditStatus.RESERVED,
                        LocalDateTime.now());

        for (CreditLedger ledger : reservedLedgers) {
            ledger.setStatus(CreditLedger.CreditStatus.CONSUMED);
            creditLedgerRepository.save(ledger);
        }

        // Record consumption transaction
        CreditTransactionLog consumeLog = new CreditTransactionLog();
        consumeLog.setUserId(log.getUserId());
        consumeLog.setTransactionId(UUID.randomUUID().toString());
        consumeLog.setType(CreditTransactionLog.TransactionType.CONSUME);
        consumeLog.setAmount(log.getAmount());
        transactionLogRepository.save(consumeLog);
    }

    @Override
    @Cacheable(value = "userBalance", key = "#userId")
    public BigDecimal getAvailableBalance(Long userId) {
        return creditLedgerRepository
                .sumRemainingAmountByUserIdAndStatusAndNotExpired(
                        userId,
                        CreditLedger.CreditStatus.ACTIVE,
                        LocalDateTime.now())
                .orElse(BigDecimal.ZERO);
    }

    @Override
    public Page<CreditTransactionLog> getTransactionHistory(Long userId, int page, int size) {
        return transactionLogRepository.findByUserIdOrderByCreatedAtDesc(
                userId,
                PageRequest.of(page, size));
    }
} 