package com.looktech.plutus.controller;

import com.looktech.plutus.domain.CreditTransactionLog;
import com.looktech.plutus.domain.SettleSessionResponse;
import com.looktech.plutus.dto.CreditSessionRequest;
import com.looktech.plutus.dto.CreditSessionSettleRequest;
import com.looktech.plutus.service.CreditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.looktech.plutus.domain.CreateSessionResponse;

@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
@Tag(name = "Credit Session", description = "Credit Session API endpoints")
public class CreditSessionController {
    private final CreditService creditService;

    @Operation(summary = "Start a credit session", description = "Start a new credit session and reserve credits")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Session started successfully",
                    content = @Content(schema = @Schema(implementation = CreateSessionResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid input parameters, duplicate request, or insufficient balance")
    })
    @PostMapping("/start")
    public ResponseEntity<CreateSessionResponse> startSession(@RequestBody CreditSessionRequest request) {
        return ResponseEntity.ok(creditService.startSession(
            request.getUserId(),
            request.getMaxAmount(),
            request.getIdempotencyId()
        ));
    }

    @Operation(summary = "Settle a credit session", description = "Settle a credit session with final amount")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Session settled successfully",
                    content = @Content(schema = @Schema(implementation = SettleSessionResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid input parameters, duplicate operation, or session not found")
    })
    @PostMapping("/{sessionId}/settle")
    public ResponseEntity<SettleSessionResponse> settleSession(
            @Parameter(description = "Session ID") @PathVariable String sessionId,
            @RequestBody CreditSessionSettleRequest request) {
        CreditTransactionLog transactionLog = creditService.settleSession(sessionId, request.getFinalAmount());
        
        SettleSessionResponse response = new SettleSessionResponse();
        response.setSessionId(sessionId);
        response.setUserId(transactionLog.getUserId());
        response.setFinalAmount(transactionLog.getAmount());
        response.setTransactionId(transactionLog.getTransactionId());
        response.setType(transactionLog.getType());
        response.setCreatedAt(transactionLog.getCreatedAt());
        
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Cancel a credit session", description = "Cancel a credit session and release reserved credits")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Session cancelled successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid session status or session not found")
    })
    @PostMapping("/{sessionId}/cancel")
    public ResponseEntity<Boolean> cancelSession(
            @Parameter(description = "Session ID") @PathVariable String sessionId) {
        creditService.cancelSession(sessionId);
        return ResponseEntity.ok(true);
    }
} 