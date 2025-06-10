package com.looktech.plutus.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "Credit reserve request")
public class CreditReserveRequest {
    @Schema(description = "User ID")
    private Long userId;

    @Schema(description = "Credit amount to reserve")
    private BigDecimal amount;

    @Schema(description = "Transaction ID for tracking")
    private String transactionId;
} 