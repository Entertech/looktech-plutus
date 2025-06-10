package com.looktech.plutus.controller;

import com.looktech.plutus.annotation.RateLimit;
import com.looktech.plutus.domain.CreditLedger;
import com.looktech.plutus.domain.CreditTransactionLog;
import com.looktech.plutus.dto.CreditGrantRequest;
import com.looktech.plutus.dto.CreditReserveRequest;
import com.looktech.plutus.dto.TransactionRequest;
import com.looktech.plutus.dto.TransactionHistoryRequest;
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
                    content = @Content(schema = @Schema(implementation = CreditTransactionLog.class))),
        @ApiResponse(responseCode = "400", description = "Invalid input parameters"),
        @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    @PostMapping("/grant")
    @RateLimit(key = "grant_credit", limit = 100, period = 60)
    public ResponseEntity<CreditTransactionLog> grantCredit(@RequestBody CreditGrantRequest request) {
        return ResponseEntity.ok(creditService.grantCredit(
            request.getUserId(),
            request.getAmount(),
            request.getSourceType(),
            request.getSourceId(),
            request.getExpiresAt()
        ));
    }

    @Operation(summary = "Reserve credits", description = "Reserve credits for a user with specified amount")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Credits reserved successfully",
                    content = @Content(schema = @Schema(implementation = List.class))),
        @ApiResponse(responseCode = "400", description = "Invalid input parameters"),
        @ApiResponse(responseCode = "409", description = "Insufficient balance"),
        @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    @PostMapping("/reserve")
    @RateLimit(key = "reserve_credit", limit = 200, period = 60)
    public ResponseEntity<List<CreditLedger>> reserveCredit(@RequestBody CreditReserveRequest request) {
        return ResponseEntity.ok(creditService.reserveCredit(
            request.getUserId(),
            request.getAmount(),
            request.getTransactionId()
        ));
    }

    @Operation(summary = "Cancel credit reservation", description = "Cancel a previously reserved credit transaction")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Reservation cancelled successfully"),
        @ApiResponse(responseCode = "404", description = "Transaction not found"),
        @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    @PostMapping("/cancel")
    @RateLimit(key = "cancel_reservation", limit = 200, period = 60)
    public ResponseEntity<Void> cancelReservation(@RequestBody TransactionRequest request) {
        creditService.cancelReservation(request.getTransactionId());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Settle credit transaction", description = "Settle a previously reserved credit transaction")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Transaction settled successfully"),
        @ApiResponse(responseCode = "404", description = "Transaction not found"),
        @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    @PostMapping("/settle")
    @RateLimit(key = "settle_credit", limit = 200, period = 60)
    public ResponseEntity<Void> settleCredit(@RequestBody TransactionRequest request) {
        creditService.settleCredit(request.getTransactionId());
        return ResponseEntity.ok().build();
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
            @RequestBody TransactionHistoryRequest request) {
        return ResponseEntity.ok(creditService.getTransactionHistory(userId, request.getPage(), request.getSize()));
    }
} 