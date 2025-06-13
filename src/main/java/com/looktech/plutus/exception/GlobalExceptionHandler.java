package com.looktech.plutus.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(CreditException.class)
    public ResponseEntity<Map<String, Object>> handleCreditException(CreditException ex) {
        log.error("CreditException occurred: {}", ex.getMessage());
        Map<String, Object> response = new HashMap<>();
        response.put("code", ex.getCode());
        response.put("message", ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
public ResponseEntity<Map<String, Object>> handleException(Exception ex) {        
        log.error("Unexpected error occurred", ex);
        Map<String, Object> response = new HashMap<>();
        response.put("code", "INTERNAL_ERROR");
        response.put("message", "An internal error occurred");
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
} 