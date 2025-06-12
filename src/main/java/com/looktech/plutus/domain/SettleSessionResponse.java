package com.looktech.plutus.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Schema(description = "Response for credit session settlement")
public class SettleSessionResponse {
    @Schema(description = "Session ID")
    private String sessionId;

    @Schema(description = "User ID")
    private Long userId;

    @Schema(description = "Final amount settled")
    private BigDecimal finalAmount;

    @Schema(description = "Transaction ID")
    private String transactionId;

    @Schema(description = "Transaction type")
    private CreditTransactionLog.TransactionType type;

    @Schema(description = "Transaction timestamp")
    private LocalDateTime createdAt;
} 