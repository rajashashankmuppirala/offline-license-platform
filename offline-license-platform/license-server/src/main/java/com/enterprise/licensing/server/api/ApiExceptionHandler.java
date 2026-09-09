package com.enterprise.licensing.server.api;

import com.enterprise.licensing.model.LicenseReasonCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public final class ApiExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiResponse<Void>> invalid(IllegalArgumentException ignored) {
        return ResponseEntity.badRequest().body(ApiResponse.denied(LicenseReasonCode.INVALID_REQUEST));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> failure(Exception ignored) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.denied(LicenseReasonCode.INTERNAL_ERROR));
    }
}
