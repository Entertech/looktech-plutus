package com.looktech.plutus.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class CreditSessionRequest {
    private Long userId;
    private BigDecimal maxAmount;
    private String requestId;
} 