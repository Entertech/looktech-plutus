package com.looktech.plutus.controller;

import com.looktech.plutus.domain.CreditTransactionLog;
import com.looktech.plutus.dto.CreditSessionRequest;
import com.looktech.plutus.dto.CreditSessionSettleRequest;
import com.looktech.plutus.dto.CreditSessionCancelRequest;
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

@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
@Tag(name = "Credit Session", description = "Credit Session API endpoints")
public class CreditSessionController {
    private final CreditService creditService;

    @Operation(summary = "Start a credit session", description = "Start a new credit session and reserve credits")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Session started successfully",
                    content = @Content(schema = @Schema(implementation = String.class))),
        @ApiResponse(responseCode = "400", description = "Invalid input parameters"),
        @ApiResponse(responseCode = "409", description = "Insufficient balance"),
        @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    @PostMapping("/start")
    public ResponseEntity<String> startSession(@RequestBody CreditSessionRequest request) {
        return ResponseEntity.ok(creditService.startSession(
            request.getUserId(),
            request.getMaxAmount(),
            request.getRequestId()
        ));
    }

    @Operation(summary = "Settle a credit session", description = "Settle a credit session with final amount")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Session settled successfully",
                    content = @Content(schema = @Schema(implementation = CreditTransactionLog.class))),
        @ApiResponse(responseCode = "400", description = "Invalid input parameters"),
        @ApiResponse(responseCode = "404", description = "Session not found"),
        @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    @PostMapping("/{sessionId}/settle")
    public ResponseEntity<CreditTransactionLog> settleSession(
            @Parameter(description = "Session ID") @PathVariable String sessionId,
            @RequestBody CreditSessionSettleRequest request) {
        return ResponseEntity.ok(creditService.settleSession(
            sessionId,
            request.getFinalAmount(),
            request.getRequestId()
        ));
    }

    @Operation(summary = "Cancel a credit session", description = "Cancel a credit session and release reserved credits")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Session cancelled successfully"),
        @ApiResponse(responseCode = "404", description = "Session not found"),
        @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    @PostMapping("/{sessionId}/cancel")
    public ResponseEntity<Void> cancelSession(
            @Parameter(description = "Session ID") @PathVariable String sessionId,
            @RequestBody CreditSessionCancelRequest request) {
        creditService.cancelSession(sessionId, request.getRequestId());
        return ResponseEntity.ok().build();
    }
} 