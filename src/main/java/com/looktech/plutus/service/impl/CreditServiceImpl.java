package com.looktech.plutus.service.impl;

import com.looktech.plutus.domain.CreditLedger;
import com.looktech.plutus.domain.CreditTransactionLog;
import com.looktech.plutus.domain.UserCreditSummary;
import com.looktech.plutus.domain.CreditConsumptionDetail;
import com.looktech.plutus.domain.CreditFreeze;
import com.looktech.plutus.domain.CreateSessionResponse;
import com.looktech.plutus.dto.BatchCreditGrantRequest;
import com.looktech.plutus.dto.BatchCreditGrantResponse;
import com.looktech.plutus.dto.CreditGrantResponse;
import com.looktech.plutus.enums.SourceType;
import com.looktech.plutus.exception.CreditException;
import com.looktech.plutus.repository.*;
import com.looktech.plutus.service.CreditService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class CreditServiceImpl implements CreditService {

    private final UserCreditSummaryRepository userCreditSummaryRepository;
    private final CreditLedgerRepository creditLedgerRepository;
    private final CreditTransactionLogRepository transactionLogRepository;
    private final CreditConsumptionDetailRepository consumptionDetailRepository;
    private final CreditFreezeRepository creditFreezeRepository;
    private final RedisTemplate<String, String> redisTemplate;

    @Override
    @Transactional
    @CacheEvict(value = "userBalance", key = "#userId")
    public CreditTransactionLog grantCredit(Long userId, BigDecimal amount, SourceType sourceType, String sourceId, LocalDateTime expiresAt, String idempotencyId) {
        // 1. Idempotency check
        String lockKey = String.format("credit:grant:%d:%s", userId, idempotencyId);
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", 24, TimeUnit.HOURS);
        if (Boolean.FALSE.equals(acquired)) {
            return transactionLogRepository.findByTransactionId(idempotencyId)
                    .orElseThrow(() -> new CreditException("DUPLICATE_REQUEST", "Duplicate idempotency ID"));
        }

        try {
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new CreditException("INVALID_AMOUNT", "Credit amount must be positive");
            }

            if (expiresAt == null || expiresAt.isBefore(LocalDateTime.now())) {
                throw new CreditException("INVALID_EXPIRATION", "Expiration time must be later than current time");
            }

            // Create credit ledger entry
            CreditLedger ledger = new CreditLedger();
            ledger.setUserId(userId);
            ledger.setRemainingAmount(amount);
            ledger.setStatus(CreditLedger.CreditStatus.ACTIVE);
            ledger.setSourceType(sourceType.toString());
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
            log.setTransactionId(idempotencyId);
            log.setType(CreditTransactionLog.TransactionType.GRANT);
            log.setAmount(amount);
            log.setSourceType(sourceType.toString());
            log.setSourceId(sourceId);
            log.setCreditId(ledger.getId());
            return transactionLogRepository.save(log);

        } catch (Exception e) {
            redisTemplate.delete(lockKey);
            throw e;
        }
    }


    @Override
    @Cacheable(value = "userBalance", key = "T(String).valueOf(#userId)")
    public BigDecimal getAvailableBalance(Long userId) {
        // Get all non-expired credits with ACTIVE status
        BigDecimal totalBalance = creditLedgerRepository
                .sumRemainingAmountByUserIdAndStatusAndNotExpired(
                        userId,
                        CreditLedger.CreditStatus.ACTIVE,
                        LocalDateTime.now())
                .orElse(BigDecimal.ZERO);
                
        // Subtract all non-expired frozen credits
        BigDecimal frozenAmount = creditFreezeRepository
                .sumAmountByUserIdAndStatusAndNotExpired(
                        userId,
                        CreditFreeze.FreezeStatus.ACTIVE,
                        LocalDateTime.now())
                .orElse(BigDecimal.ZERO);
                
        return totalBalance.subtract(frozenAmount);
    }

    @Override
    public Page<CreditTransactionLog> getTransactionHistory(Long userId, int page, int size) {
        return transactionLogRepository.findByUserIdOrderByCreatedAtDesc(
                userId,
                PageRequest.of(page, size));
    }

    @Override
    @Transactional
    @CacheEvict(value = "userBalance", key = "T(String).valueOf(#userId)")
    public CreditTransactionLog deductCredit(Long userId, BigDecimal amount, SourceType sourceType, String sourceId, String idempotencyId) {
        // 1. Idempotency check
        String lockKey = String.format("credit:deduct:%d:%s", userId, idempotencyId);
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", 24, TimeUnit.HOURS);
        if (Boolean.FALSE.equals(acquired)) {
            // If idempotencyId exists, return the existing transaction record
            return transactionLogRepository.findByTransactionId(idempotencyId)
                    .orElseThrow(() -> new CreditException("DUPLICATE_REQUEST", "Duplicate idempotency ID"));
        }

        try {
            // 2. Parameter validation
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new CreditException("INVALID_AMOUNT", "Credit amount must be positive");
            }

            // 3. Check balance
            BigDecimal availableBalance = getAvailableBalance(userId);
            if (availableBalance.compareTo(amount) < 0) {
                throw new CreditException("INSUFFICIENT_BALANCE", "Insufficient credit balance");
            }

            // 4. Get available credit batches (sorted by expiration time)
            List<CreditLedger> availableLedgers = creditLedgerRepository
                    .findByUserIdAndStatusAndExpiresAtAfterOrderByExpiresAtAsc(
                            userId,
                            CreditLedger.CreditStatus.ACTIVE,
                            LocalDateTime.now());

            // 5. Deduct credits and record details
            BigDecimal remainingAmount = amount;
            for (CreditLedger ledger : availableLedgers) {
                if (remainingAmount.compareTo(BigDecimal.ZERO) <= 0) {
                    break;
                }

                BigDecimal deductAmount = ledger.getRemainingAmount().min(remainingAmount);
                ledger.setRemainingAmount(ledger.getRemainingAmount().subtract(deductAmount));
                if (ledger.getRemainingAmount().compareTo(BigDecimal.ZERO) == 0) {
                    ledger.setStatus(CreditLedger.CreditStatus.CONSUMED);
                }
                creditLedgerRepository.save(ledger);

                // Record consumption details
                CreditConsumptionDetail detail = new CreditConsumptionDetail();
                detail.setTransactionId(idempotencyId);
                detail.setLedgerId(ledger.getId());
                detail.setAmount(deductAmount);
                consumptionDetailRepository.save(detail);

                remainingAmount = remainingAmount.subtract(deductAmount);
            }

            // 6. Update user total balance
            UserCreditSummary summary = userCreditSummaryRepository.findByUserId(userId)
                    .orElseThrow(() -> new CreditException("USER_NOT_FOUND", "User credit summary not found"));
            summary.setTotalBalance(summary.getTotalBalance().subtract(amount));
            userCreditSummaryRepository.save(summary);

            // 7. Record transaction log
            CreditTransactionLog log = new CreditTransactionLog();
            log.setUserId(userId);
            log.setTransactionId(idempotencyId);
            log.setType(CreditTransactionLog.TransactionType.CONSUME);
            log.setAmount(amount);
            log.setSourceType(sourceType.toString());
            log.setSourceId(sourceId);
            return transactionLogRepository.save(log);

        } catch (Exception e) {
            redisTemplate.delete(lockKey);
            throw e;
        }
    }

    @Override
    @Transactional
    @CacheEvict(value = "userBalance", key = "T(String).valueOf(#userId)")
    public CreateSessionResponse startSession(Long userId, BigDecimal maxAmount, String idempotencyId) {
        // 1. Idempotency check
        String lockKey = String.format("credit:session:start:%d:%s", userId, idempotencyId);
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", 24, TimeUnit.HOURS);
        if (Boolean.FALSE.equals(acquired)) {
            return transactionLogRepository.findByTransactionId(idempotencyId)
                    .map(log -> {
                        CreateSessionResponse response = new CreateSessionResponse();
                        response.setSessionId(log.getSourceId());
                        response.setUserId(log.getUserId());
                        response.setAmount(log.getAmount());
                        response.setIdempotencyId(idempotencyId);
                        return response;
                    })
                    .orElseThrow(() -> new CreditException("DUPLICATE_REQUEST", "Duplicate idempotency ID"));
        }

        try {
            // 2. Parameter validation
            if (maxAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new CreditException("INVALID_AMOUNT", "Max amount must be positive");
            }

            // 3. Check available balance
            BigDecimal availableBalance = getAvailableBalance(userId);
            if (availableBalance.compareTo(maxAmount) < 0) {
                throw new CreditException("INSUFFICIENT_BALANCE", "Insufficient credit balance");
            }

            // 4. Generate session ID
            String sessionId = UUID.randomUUID().toString();

            // 5. Create freeze record
            CreditFreeze freeze = new CreditFreeze();
            freeze.setUserId(userId);
            freeze.setSessionId(sessionId);
            freeze.setAmount(maxAmount);
            freeze.setRequestId(idempotencyId);
            freeze.setStatus(CreditFreeze.FreezeStatus.ACTIVE);
            freeze.setExpiresAt(LocalDateTime.now().plusHours(24)); // Set freeze expiration time
            freeze.setCreatedAt(LocalDateTime.now());
            creditFreezeRepository.save(freeze);

            // 6. Record reservation transaction
            CreditTransactionLog log = new CreditTransactionLog();
            log.setUserId(userId);
            log.setTransactionId(idempotencyId);
            log.setType(CreditTransactionLog.TransactionType.RESERVE);
            log.setAmount(maxAmount);
            log.setSourceType("SESSION");
            log.setSourceId(sessionId);
            transactionLogRepository.save(log);

            // 7. Create and return response object
            CreateSessionResponse response = new CreateSessionResponse();
            response.setSessionId(sessionId);
            response.setUserId(userId);
            response.setAmount(maxAmount);
            response.setIdempotencyId(idempotencyId);
            return response;

        } catch (Exception e) {
            redisTemplate.delete(lockKey);
            throw e;
        }
    }

    @Override
    @Transactional
    @CacheEvict(value = "userBalance", key = "T(String).valueOf(#freeze.userId)", condition = "#freeze != null")
    public CreditTransactionLog settleSession(String sessionId, BigDecimal finalAmount) {
        // 1. Idempotency check
        String lockKey = "credit:session:settle:" + sessionId;
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", 24, TimeUnit.HOURS);
        if (Boolean.FALSE.equals(acquired)) {
            throw new CreditException("DUPLICATE_OPERATION", "Session already settled");
        }

        try {
            // 2. Get freeze record
            CreditFreeze freeze = creditFreezeRepository.findBySessionId(sessionId)
                    .orElseThrow(() -> new CreditException("SESSION_NOT_FOUND", "Session not found"));

            // 3. Validate amount
            if (finalAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new CreditException("INVALID_AMOUNT", "Final amount must be positive");
            }
            if (finalAmount.compareTo(freeze.getAmount()) > 0) {
                log.error("Final amount exceeds frozen amount: finalAmount={}, freezeAmount={}, sessionId={}", finalAmount, freeze.getAmount(), sessionId);
            }
            // 4. Update freeze status
            freeze.setStatus(CreditFreeze.FreezeStatus.CONSUMED);
            creditFreezeRepository.save(freeze);

            // 5. Actually deduct credits
            List<CreditLedger> availableLedgers = creditLedgerRepository
                    .findByUserIdAndStatusAndExpiresAtAfterOrderByExpiresAtAsc(
                            freeze.getUserId(),
                            CreditLedger.CreditStatus.ACTIVE,
                            LocalDateTime.now());

            String transactionId = UUID.randomUUID().toString();
            log.info("Starting credit settlement: sessionId={}, transactionId={}, finalAmount={}", sessionId, transactionId, finalAmount);
            
            BigDecimal remainingAmount = finalAmount;
            for (CreditLedger ledger : availableLedgers) {
                if (remainingAmount.compareTo(BigDecimal.ZERO) <= 0) {
                    break;
                }

                BigDecimal deductAmount = ledger.getRemainingAmount().min(remainingAmount);
                ledger.setRemainingAmount(ledger.getRemainingAmount().subtract(deductAmount));
                if (ledger.getRemainingAmount().compareTo(BigDecimal.ZERO) == 0) {
                    ledger.setStatus(CreditLedger.CreditStatus.CONSUMED);
                }
                creditLedgerRepository.save(ledger);

                // Record consumption details
                CreditConsumptionDetail detail = new CreditConsumptionDetail();
                detail.setTransactionId(transactionId);
                detail.setLedgerId(ledger.getId());
                detail.setAmount(deductAmount);
                consumptionDetailRepository.save(detail);

                remainingAmount = remainingAmount.subtract(deductAmount);
            }

            // 6. Record consumption transaction
            CreditTransactionLog consumeLog = new CreditTransactionLog();
            consumeLog.setUserId(freeze.getUserId());
            consumeLog.setTransactionId(transactionId);
            consumeLog.setType(CreditTransactionLog.TransactionType.CONSUME);
            consumeLog.setAmount(finalAmount);
            consumeLog.setSourceType("SESSION");
            consumeLog.setSourceId(sessionId);
            CreditTransactionLog savedLog = transactionLogRepository.save(consumeLog);
            
            log.info("Credit settlement completed: sessionId={}, transactionId={}, finalAmount={}", sessionId, transactionId, finalAmount);
            return savedLog;

        } catch (Exception e) {
            log.error("Error during credit settlement: sessionId={}, error={}", sessionId, e.getMessage(), e);
            redisTemplate.delete(lockKey);
            throw e;
        }
    }

    @Override
    @Transactional
    @CacheEvict(value = "userBalance", key = "T(String).valueOf(#freeze.userId)", condition = "#freeze != null")
    public void cancelSession(String sessionId) {
        // 1. Idempotency check
        String lockKey = "credit:session:cancel:" + sessionId;
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", 24, TimeUnit.HOURS);
        if (Boolean.FALSE.equals(acquired)) {
            return;
        }

        try {
            // 2. Get freeze record
            CreditFreeze freeze = creditFreezeRepository.findBySessionId(sessionId)
                    .orElseThrow(() -> new CreditException("SESSION_NOT_FOUND", "Session not found"));

            // 3. Check session status
            if (freeze.getStatus() != CreditFreeze.FreezeStatus.ACTIVE) {
                log.warn("Cannot cancel session: sessionId={}, currentStatus={}", sessionId, freeze.getStatus());
                throw new CreditException("INVALID_SESSION_STATUS", 
                    String.format("Cannot cancel session with status: %s", freeze.getStatus()));
            }

            // 4. Update freeze status
            freeze.setStatus(CreditFreeze.FreezeStatus.CANCELLED);
            creditFreezeRepository.save(freeze);

            // 5. Record cancellation transaction
            String transactionId = UUID.randomUUID().toString();
            log.info("Starting credit session cancellation: sessionId={}, transactionId={}", sessionId, transactionId);
            
            CreditTransactionLog cancelLog = new CreditTransactionLog();
            cancelLog.setUserId(freeze.getUserId());
            cancelLog.setTransactionId(transactionId);
            cancelLog.setType(CreditTransactionLog.TransactionType.CANCEL);
            cancelLog.setAmount(freeze.getAmount());
            cancelLog.setSourceType("SESSION");
            cancelLog.setSourceId(sessionId);
            transactionLogRepository.save(cancelLog);
            
            log.info("Credit session cancellation completed: sessionId={}, transactionId={}", sessionId, transactionId);

        } catch (Exception e) {
            log.error("Error during credit session cancellation: sessionId={}, error={}", sessionId, e.getMessage(), e);
            redisTemplate.delete(lockKey);
            throw e;
        }
    }

    @Override
    @Transactional
    public BatchCreditGrantResponse batchGrantCredit(List<BatchCreditGrantRequest.CreditGrantItem> items) {
        // 收集所有需要清除缓存的用户ID
        Set<Long> userIds = items.stream()
            .map(BatchCreditGrantRequest.CreditGrantItem::getUserId)
            .collect(Collectors.toSet());
        
        // 批量处理
        List<CreditGrantResponse> successResults = new ArrayList<>();
        List<BatchCreditGrantResponse.FailedGrantResult> failResults = new ArrayList<>();
        
        // 使用批量插入优化数据库操作
        List<CreditLedger> ledgers = new ArrayList<>();
        List<UserCreditSummary> summaries = new ArrayList<>();
        List<CreditTransactionLog> logs = new ArrayList<>();
        
        // 使用Redis Pipeline批量处理锁
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (BatchCreditGrantRequest.CreditGrantItem item : items) {
                String lockKey = String.format("credit:grant:%d:%s", item.getUserId(), item.getIdempotencyId());
                connection.stringCommands().setNX(lockKey.getBytes(), "1".getBytes());
                connection.keyCommands().expire(lockKey.getBytes(), 24 * 3600); // 24小时过期
            }
            return null;
        });
        
        for (BatchCreditGrantRequest.CreditGrantItem item : items) {
            try {
                // 检查幂等性
                String lockKey = String.format("credit:grant:%d:%s", item.getUserId(), item.getIdempotencyId());
                if (Boolean.FALSE.equals(redisTemplate.hasKey(lockKey))) {
                    throw new CreditException("DUPLICATE_REQUEST", "Duplicate idempotency ID");
                }
                
                // 构建对象但不立即保存
                CreditLedger ledger = new CreditLedger();
                ledger.setUserId(item.getUserId());
                ledger.setRemainingAmount(item.getAmount());
                ledger.setStatus(CreditLedger.CreditStatus.ACTIVE);
                ledger.setSourceType(item.getSourceType().toString());
                ledger.setSourceId(item.getSourceId());
                ledger.setExpiresAt(item.getExpiresAt());
                
                UserCreditSummary summary = userCreditSummaryRepository.findByUserId(item.getUserId())
                    .orElseGet(() -> {
                        UserCreditSummary newSummary = new UserCreditSummary();
                        newSummary.setUserId(item.getUserId());
                        newSummary.setTotalBalance(BigDecimal.ZERO);
                        return newSummary;
                    });
                summary.setTotalBalance(summary.getTotalBalance().add(item.getAmount()));
                
                CreditTransactionLog log = new CreditTransactionLog();
                log.setUserId(item.getUserId());
                log.setTransactionId(item.getIdempotencyId());
                log.setType(CreditTransactionLog.TransactionType.GRANT);
                log.setAmount(item.getAmount());
                log.setSourceType(item.getSourceType().toString());
                log.setSourceId(item.getSourceId());
                
                ledgers.add(ledger);
                summaries.add(summary);
                logs.add(log);
                
                successResults.add(CreditGrantResponse.fromTransactionLog(log));
            } catch (Exception e) {
                log.error("Failed to grant credit for user {}: {}", item.getUserId(), e.getMessage());
                failResults.add(BatchCreditGrantResponse.FailedGrantResult.builder()
                    .userId(item.getUserId())
                    .errorCode(e instanceof CreditException ? ((CreditException) e).getCode() : "UNKNOWN_ERROR")
                    .errorMessage(e.getMessage())
                    .build());
            }
        }
        
        // 批量保存
        if (!ledgers.isEmpty()) {
            creditLedgerRepository.saveAll(ledgers);
        }
        if (!summaries.isEmpty()) {
            userCreditSummaryRepository.saveAll(summaries);
        }
        if (!logs.isEmpty()) {
            transactionLogRepository.saveAll(logs);
        }
        
        // 最后一次性清除所有相关用户的缓存
        if (!userIds.isEmpty()) {
            redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                for (Long userId : userIds) {
                    connection.del(("userBalance::" + userId).getBytes());
                }
                return null;
            });
        }
        
        return BatchCreditGrantResponse.builder()
            .successCount(successResults.size())
            .failCount(failResults.size())
            .successResults(successResults)
            .failResults(failResults)
            .build();
    }
} 