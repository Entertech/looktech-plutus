package com.looktech.plutus.exception;

public class CreditException extends RuntimeException {
    
    private final String code;
    
    public CreditException(String code, String message) {
        super(message);
        this.code = code;
    }
    
    public CreditException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
    
    public String getCode() {
        return code;
    }
} 