package com.looktech.plutus.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Transaction request")
public class TransactionRequest {
    @Schema(description = "Transaction ID")
    private String transactionId;
} 