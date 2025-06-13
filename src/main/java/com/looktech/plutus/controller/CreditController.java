package com.looktech.plutus.controller;

import com.looktech.plutus.annotation.RateLimit;
import com.looktech.plutus.domain.CreditTransactionLog;
import com.looktech.plutus.dto.*;
import com.looktech.plutus.service.CreditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/credits")
@RequiredArgsConstructor
@Tag(name = "Credit Service", description = "Credit Service API endpoints")
public class CreditController {

    private final CreditService creditService;

    @Operation(summary = "Grant credits to user", description = "Grant credits to a user with specified amount and expiration time")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Credits granted successfully",
                    content = @Content(schema = @Schema(implementation = CreditGrantResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid input parameters"),
        @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    @PostMapping("/grant")
    @RateLimit(key = "grant_credit", limit = 100, period = 60)
    public ResponseEntity<CreditGrantResponse> grantCredit(@RequestBody CreditGrantRequest request) {
        CreditTransactionLog log = creditService.grantCredit(
            request.getUserId(),
            request.getAmount(),
            request.getSourceType(),
            request.getSourceId(),
            request.getExpiresAt(),
            request.getIdempotencyId()
        );
        return ResponseEntity.ok(CreditGrantResponse.fromTransactionLog(log));
    }

    @Operation(summary = "Get user's available balance", description = "Get the available credit balance for a user")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Balance retrieved successfully",
                    content = @Content(schema = @Schema(implementation = BigDecimal.class))),
        @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    @GetMapping("/users/{userId}/balance")
    @RateLimit(key = "get_balance", limit = 1000, period = 60)
    public ResponseEntity<BigDecimal> getAvailableBalance(
            @Parameter(description = "User ID") @PathVariable Long userId) {
        return ResponseEntity.ok(creditService.getAvailableBalance(userId));
    }

    @Operation(summary = "Get user's transaction history", description = "Get the transaction history for a user with pagination")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Transaction history retrieved successfully",
                    content = @Content(schema = @Schema(implementation = Page.class))),
        @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    @GetMapping("/users/{userId}/transactions")
    @RateLimit(key = "get_transactions", limit = 500, period = 60)
    public ResponseEntity<Page<CreditTransactionLog>> getTransactionHistory(
            @Parameter(description = "User ID") @PathVariable Long userId,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(creditService.getTransactionHistory(userId, page, size));
    }

    @Operation(summary = "Deduct credits synchronously", description = "Deduct credits from user's balance immediately")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Credits deducted successfully",
                    content = @Content(schema = @Schema(implementation = CreditTransactionLog.class))),
        @ApiResponse(responseCode = "400", description = "Invalid input parameters"),
        @ApiResponse(responseCode = "409", description = "Insufficient balance"),
        @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    @PostMapping("/deduct")
    @RateLimit(key = "deduct_credit", limit = 200, period = 60)
    public ResponseEntity<CreditTransactionLog> deductCredit(@RequestBody CreditDeductRequest request) {
        return ResponseEntity.ok(creditService.deductCredit(
            request.getUserId(),
            request.getAmount(),
            request.getSourceType(),
            request.getSourceId(),
            request.getIdempotencyId()
        ));
    }

    @Operation(summary = "Batch grant credits to users", description = "Grant credits to multiple users with specified amounts and expiration times")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Credits granted successfully",
                    content = @Content(schema = @Schema(implementation = BatchCreditGrantResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid input parameters"),
        @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    @PostMapping("/batch-grant")
    @RateLimit(key = "batch_grant_credit", limit = 50, period = 60)
    public ResponseEntity<BatchCreditGrantResponse> batchGrantCredit(@RequestBody BatchCreditGrantRequest request) {
        return ResponseEntity.ok(creditService.batchGrantCredit(request.getItems()));
    }
} 