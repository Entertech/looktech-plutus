package com.looktech.plutus.dto;

import com.looktech.plutus.domain.CreditTransactionLog;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@Schema(description = "Credit grant response")
public class CreditGrantResponse {
    @Schema(description = "Transaction ID")
    private String transactionId;

    @Schema(description = "User ID")
    private Long userId;

    @Schema(description = "Granted amount")
    private BigDecimal amount;

    @Schema(description = "Source type")
    private String sourceType;

    @Schema(description = "Source ID")
    private String sourceId;

    @Schema(description = "Expiration time")
    private LocalDateTime expiresAt;

    @Schema(description = "Transaction time")
    private LocalDateTime createdAt;

    public static CreditGrantResponse fromTransactionLog(CreditTransactionLog log) {
        return CreditGrantResponse.builder()
                .transactionId(log.getTransactionId())
                .userId(log.getUserId())
                .amount(log.getAmount())
                .sourceType(log.getSourceType())
                .sourceId(log.getSourceId())
                .createdAt(log.getCreatedAt())
                .build();
    }
} 