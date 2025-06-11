package com.looktech.plutus.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class CreditSessionSettleRequest {
    private BigDecimal finalAmount;
    private String requestId;
} 