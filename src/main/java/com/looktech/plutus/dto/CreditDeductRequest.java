package com.looktech.plutus.dto;

import com.looktech.plutus.enums.SourceType;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class CreditDeductRequest {
    private Long userId;
    private BigDecimal amount;
    private SourceType sourceType;
    private String sourceId;
    private String idempotencyId;
} 