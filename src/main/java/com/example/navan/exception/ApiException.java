package com.example.navan.exception;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String existingId;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
        this.existingId = null;
    }

    public ApiException(HttpStatus status, String message, String existingId) {
        super(message);
        this.status = status;
        this.existingId = existingId;
    }

    public HttpStatus getStatus() { return status; }
    public String getExistingId() { return existingId; }
}
