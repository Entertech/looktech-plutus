package com.looktech.plutus.controller;

import com.looktech.plutus.domain.CreditTransactionLog;
import com.looktech.plutus.service.CreditService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
public class CreditSessionController {
    private final CreditService creditService;

    @PostMapping("/start")
    public ResponseEntity<String> startSession(
            @RequestParam Long userId,
            @RequestParam BigDecimal maxAmount,
            @RequestParam String requestId) {
        return ResponseEntity.ok(creditService.startSession(userId, maxAmount, requestId));
    }

    @PostMapping("/{sessionId}/settle")
    public ResponseEntity<CreditTransactionLog> settleSession(
            @PathVariable String sessionId,
            @RequestParam BigDecimal finalAmount,
            @RequestParam String requestId) {
        return ResponseEntity.ok(creditService.settleSession(sessionId, finalAmount, requestId));
    }

    @PostMapping("/{sessionId}/cancel")
    public ResponseEntity<Void> cancelSession(
            @PathVariable String sessionId,
            @RequestParam String requestId) {
        creditService.cancelSession(sessionId, requestId);
        return ResponseEntity.ok().build();
    }
} 