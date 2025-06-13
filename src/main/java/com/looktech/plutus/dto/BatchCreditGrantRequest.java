package com.looktech.plutus.dto;

import com.looktech.plutus.enums.SourceType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "Batch credit grant request")
public class BatchCreditGrantRequest {
    @Schema(description = "List of credit grant requests")
    private List<CreditGrantItem> items;

    @Data
    @Schema(description = "Credit grant item")
    public static class CreditGrantItem {
        @Schema(description = "User ID")
        private Long userId;

        @Schema(description = "Credit amount")
        private BigDecimal amount;

        @Schema(description = "Source type of the credit")
        private SourceType sourceType;

        @Schema(description = "Source ID of the credit")
        private String sourceId;

        @Schema(description = "Expiration time of the credit")
        private LocalDateTime expiresAt;

        @Schema(description = "Unique ID for idempotency")
        private String idempotencyId;
    }
} 