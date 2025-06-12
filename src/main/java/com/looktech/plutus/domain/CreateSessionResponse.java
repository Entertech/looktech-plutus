package com.looktech.plutus.domain;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class CreateSessionResponse {
    private String sessionId;
    private Long userId;
    private BigDecimal amount;
    private String idempotencyId;
} 