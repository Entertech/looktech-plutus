package com.looktech.plutus.domain;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "credit_transaction_logs", indexes = {
    @Index(name = "idx_transaction_id", columnList = "transaction_id")
})
public class CreditTransactionLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "transaction_id", nullable = false)
    private String transactionId;

    @Column(name = "type", nullable = false)
    @Enumerated(EnumType.STRING)
    private TransactionType type;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "source_type")
    private String sourceType;

    @Column(name = "source_id")
    private String sourceId;

    @Column(name = "credit_id")
    private Long creditId;

    @Column(name = "description")
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum TransactionType {
        GRANT,
        RESERVE,
        CONSUME,
        CANCEL,
        EXPIRE
    }
} 