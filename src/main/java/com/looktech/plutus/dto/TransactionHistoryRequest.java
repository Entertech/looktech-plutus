package com.looktech.plutus.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Transaction history request")
public class TransactionHistoryRequest {
    @Schema(description = "Page number (0-based)")
    private int page = 0;

    @Schema(description = "Page size")
    private int size = 20;
} 