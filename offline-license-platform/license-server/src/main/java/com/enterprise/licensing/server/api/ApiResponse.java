package com.enterprise.licensing.server.api;

import com.enterprise.licensing.model.LicenseReasonCode;

import java.time.Instant;

public record ApiResponse<T>(boolean success, LicenseReasonCode reasonCode, Instant timestamp, T data) {
    public static <T> ApiResponse<T> success(T data) { return new ApiResponse<>(true, LicenseReasonCode.LICENSE_VALID, Instant.now(), data); }
    public static <T> ApiResponse<T> denied(LicenseReasonCode reason) { return new ApiResponse<>(false, reason, Instant.now(), null); }
}
