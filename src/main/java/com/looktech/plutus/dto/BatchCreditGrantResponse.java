package com.looktech.plutus.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.Builder;

import java.util.List;

@Data
@Builder
@Schema(description = "Batch credit grant response")
public class BatchCreditGrantResponse {
    @Schema(description = "Total number of successful grants")
    private int successCount;

    @Schema(description = "Total number of failed grants")
    private int failCount;

    @Schema(description = "List of successful grant results")
    private List<CreditGrantResponse> successResults;

    @Schema(description = "List of failed grant results")
    private List<FailedGrantResult> failResults;

    @Data
    @Builder
    @Schema(description = "Failed grant result")
    public static class FailedGrantResult {
        @Schema(description = "User ID")
        private Long userId;

        @Schema(description = "Error code")
        private String errorCode;

        @Schema(description = "Error message")
        private String errorMessage;
    }
} 