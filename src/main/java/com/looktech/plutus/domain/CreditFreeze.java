package com.looktech.plutus.domain;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "credit_freeze")
public class CreditFreeze {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private Long userId;
    private String sessionId;
    private BigDecimal amount;
    private LocalDateTime expiresAt;
    private String requestId;
    private LocalDateTime createdAt;
    
    @Enumerated(EnumType.STRING)
    private FreezeStatus status;
    
    public enum FreezeStatus {
        ACTIVE,
        CONSUMED,
        CANCELLED
    }
} 