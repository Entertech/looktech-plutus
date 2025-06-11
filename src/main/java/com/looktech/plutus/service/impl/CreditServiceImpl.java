package com.looktech.plutus.service.impl;

import com.looktech.plutus.domain.CreditLedger;
import com.looktech.plutus.domain.CreditTransactionLog;
import com.looktech.plutus.domain.UserCreditSummary;
import com.looktech.plutus.domain.CreditConsumptionDetail;
import com.looktech.plutus.exception.CreditException;
import com.looktech.plutus.repository.CreditLedgerRepository;
import com.looktech.plutus.repository.CreditTransactionLogRepository;
import com.looktech.plutus.repository.UserCreditSummaryRepository;
import com.looktech.plutus.repository.CreditConsumptionDetailRepository;
import com.looktech.plutus.service.CreditService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class CreditServiceImpl implements CreditService {

    private final UserCreditSummaryRepository userCreditSummaryRepository;
    private final CreditLedgerRepository creditLedgerRepository;
    private final CreditTransactionLogRepository transactionLogRepository;
    private final CreditConsumptionDetailRepository consumptionDetailRepository;
    private final RedisTemplate<String, String> redisTemplate;

    @Override
    @Transactional
    @CacheEvict(value = "userBalance", key = "#userId")
    public CreditTransactionLog grantCredit(Long userId, BigDecimal amount, String sourceType, String sourceId, LocalDateTime expiresAt) {
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
        log.setCreditId(ledger.getId());
        return transactionLogRepository.save(log);
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

    @Override
    @Transactional
    @CacheEvict(value = "userBalance", key = "#userId")
    public CreditTransactionLog deductCredit(Long userId, BigDecimal amount, String sourceType, String sourceId, String requestId) {
        // 1. 幂等性检查
        String lockKey = "credit:deduct:" + requestId;
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", 24, TimeUnit.HOURS);
        if (Boolean.FALSE.equals(acquired)) {
            // 如果requestId已存在，返回已存在的交易记录
            return transactionLogRepository.findByTransactionId(requestId)
                    .orElseThrow(() -> new CreditException("DUPLICATE_REQUEST", "Duplicate request ID"));
        }

        try {
            // 2. 参数验证
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new CreditException("INVALID_AMOUNT", "Credit amount must be positive");
            }

            // 3. 检查余额
            BigDecimal availableBalance = getAvailableBalance(userId);
            if (availableBalance.compareTo(amount) < 0) {
                throw new CreditException("INSUFFICIENT_BALANCE", "Insufficient credit balance");
            }

            // 4. 获取可用的积分批次（按过期时间排序）
            List<CreditLedger> availableLedgers = creditLedgerRepository
                    .findByUserIdAndStatusAndExpiresAtAfterOrderByExpiresAtAsc(
                            userId,
                            CreditLedger.CreditStatus.ACTIVE,
                            LocalDateTime.now());

            // 5. 扣减积分并记录明细
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

                // 记录消费明细
                CreditConsumptionDetail detail = new CreditConsumptionDetail();
                detail.setTransactionId(requestId);
                detail.setLedgerId(ledger.getId());
                detail.setAmount(deductAmount);
                consumptionDetailRepository.save(detail);

                remainingAmount = remainingAmount.subtract(deductAmount);
            }

            // 6. 更新用户总余额
            UserCreditSummary summary = userCreditSummaryRepository.findByUserId(userId)
                    .orElseThrow(() -> new CreditException("USER_NOT_FOUND", "User credit summary not found"));
            summary.setTotalBalance(summary.getTotalBalance().subtract(amount));
            userCreditSummaryRepository.save(summary);

            // 7. 记录交易日志
            CreditTransactionLog log = new CreditTransactionLog();
            log.setUserId(userId);
            log.setTransactionId(requestId);
            log.setType(CreditTransactionLog.TransactionType.CONSUME);
            log.setAmount(amount);
            log.setSourceType(sourceType);
            log.setSourceId(sourceId);
            return transactionLogRepository.save(log);

        } catch (Exception e) {
            // 发生异常时释放锁
            redisTemplate.delete(lockKey);
            throw e;
        }
    }

    @Override
    @Transactional
    @CacheEvict(value = "userBalance", key = "#userId")
    public String startSession(Long userId, BigDecimal maxAmount, String requestId) {
        // 1. 幂等性检查
        String lockKey = "credit:session:start:" + requestId;
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", 24, TimeUnit.HOURS);
        if (Boolean.FALSE.equals(acquired)) {
            // 如果requestId已存在，返回已存在的会话ID
            return transactionLogRepository.findByTransactionId(requestId)
                    .map(log -> log.getSourceId()) // sourceId 存储会话ID
                    .orElseThrow(() -> new CreditException("DUPLICATE_REQUEST", "Duplicate request ID"));
        }

        try {
            // 2. 参数验证
            if (maxAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new CreditException("INVALID_AMOUNT", "Max amount must be positive");
            }

            // 3. 检查余额
            BigDecimal availableBalance = getAvailableBalance(userId);
            if (availableBalance.compareTo(maxAmount) < 0) {
                throw new CreditException("INSUFFICIENT_BALANCE", "Insufficient credit balance");
            }

            // 4. 生成会话ID
            String sessionId = UUID.randomUUID().toString();

            // 5. 预留积分
            List<CreditLedger> reservedLedgers = creditLedgerRepository
                    .findByUserIdAndStatusAndExpiresAtAfterOrderByExpiresAtAsc(
                            userId,
                            CreditLedger.CreditStatus.ACTIVE,
                            LocalDateTime.now());

            BigDecimal remainingAmount = maxAmount;
            for (CreditLedger ledger : reservedLedgers) {
                if (remainingAmount.compareTo(BigDecimal.ZERO) <= 0) {
                    break;
                }

                BigDecimal reserveAmount = ledger.getRemainingAmount().min(remainingAmount);
                ledger.setRemainingAmount(ledger.getRemainingAmount().subtract(reserveAmount));
                ledger.setStatus(CreditLedger.CreditStatus.RESERVED);
                creditLedgerRepository.save(ledger);

                remainingAmount = remainingAmount.subtract(reserveAmount);
            }

            // 6. 记录预留交易
            CreditTransactionLog log = new CreditTransactionLog();
            log.setUserId(userId);
            log.setTransactionId(requestId);
            log.setType(CreditTransactionLog.TransactionType.RESERVE);
            log.setAmount(maxAmount);
            log.setSourceType("SESSION");
            log.setSourceId(sessionId);
            transactionLogRepository.save(log);

            return sessionId;

        } catch (Exception e) {
            redisTemplate.delete(lockKey);
            throw e;
        }
    }

    @Override
    @Transactional
    @CacheEvict(value = "userBalance", key = "#result.userId")
    public CreditTransactionLog settleSession(String sessionId, BigDecimal finalAmount, String requestId) {
        // 1. 幂等性检查
        String lockKey = "credit:session:settle:" + requestId;
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", 24, TimeUnit.HOURS);
        if (Boolean.FALSE.equals(acquired)) {
            return transactionLogRepository.findByTransactionId(requestId)
                    .orElseThrow(() -> new CreditException("DUPLICATE_REQUEST", "Duplicate request ID"));
        }

        try {
            // 2. 获取预留交易记录
            CreditTransactionLog reserveLog = transactionLogRepository.findBySourceId(sessionId)
                    .orElseThrow(() -> new CreditException("SESSION_NOT_FOUND", "Session not found"));

            // 3. 验证金额
            if (finalAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new CreditException("INVALID_AMOUNT", "Final amount must be positive");
            }
            if (finalAmount.compareTo(reserveLog.getAmount()) > 0) {
                throw new CreditException("INVALID_AMOUNT", "Final amount exceeds reserved amount");
            }

            // 4. 更新预留积分状态为已消费
            List<CreditLedger> reservedLedgers = creditLedgerRepository
                    .findByUserIdAndStatusAndExpiresAtAfterOrderByExpiresAtAsc(
                            reserveLog.getUserId(),
                            CreditLedger.CreditStatus.RESERVED,
                            LocalDateTime.now());

            for (CreditLedger ledger : reservedLedgers) {
                ledger.setStatus(CreditLedger.CreditStatus.CONSUMED);
                creditLedgerRepository.save(ledger);
            }

            // 5. 记录消费交易
            CreditTransactionLog consumeLog = new CreditTransactionLog();
            consumeLog.setUserId(reserveLog.getUserId());
            consumeLog.setTransactionId(requestId);
            consumeLog.setType(CreditTransactionLog.TransactionType.CONSUME);
            consumeLog.setAmount(finalAmount);
            consumeLog.setSourceType("SESSION");
            consumeLog.setSourceId(sessionId);
            return transactionLogRepository.save(consumeLog);

        } catch (Exception e) {
            redisTemplate.delete(lockKey);
            throw e;
        }
    }

    @Override
    @Transactional
    @CacheEvict(value = "userBalance", key = "#result.userId")
    public void cancelSession(String sessionId, String requestId) {
        // 1. 幂等性检查
        String lockKey = "credit:session:cancel:" + requestId;
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", 24, TimeUnit.HOURS);
        if (Boolean.FALSE.equals(acquired)) {
            return;
        }

        try {
            // 2. 获取预留交易记录
            CreditTransactionLog reserveLog = transactionLogRepository.findBySourceId(sessionId)
                    .orElseThrow(() -> new CreditException("SESSION_NOT_FOUND", "Session not found"));

            // 3. 恢复预留积分状态
            List<CreditLedger> reservedLedgers = creditLedgerRepository
                    .findByUserIdAndStatusAndExpiresAtAfterOrderByExpiresAtAsc(
                            reserveLog.getUserId(),
                            CreditLedger.CreditStatus.RESERVED,
                            LocalDateTime.now());

            for (CreditLedger ledger : reservedLedgers) {
                ledger.setStatus(CreditLedger.CreditStatus.ACTIVE);
                creditLedgerRepository.save(ledger);
            }

            // 4. 记录取消交易
            CreditTransactionLog cancelLog = new CreditTransactionLog();
            cancelLog.setUserId(reserveLog.getUserId());
            cancelLog.setTransactionId(requestId);
            cancelLog.setType(CreditTransactionLog.TransactionType.CANCEL);
            cancelLog.setAmount(reserveLog.getAmount());
            cancelLog.setSourceType("SESSION");
            cancelLog.setSourceId(sessionId);
            transactionLogRepository.save(cancelLog);

        } catch (Exception e) {
            redisTemplate.delete(lockKey);
            throw e;
        }
    }
} 