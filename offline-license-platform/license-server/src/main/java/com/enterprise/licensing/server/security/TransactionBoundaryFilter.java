package com.enterprise.licensing.server.security;

import com.enterprise.licensing.storage.sqlite.SqliteImmediateTransactionRunner;
import com.enterprise.licensing.storage.integrity.AuthoritativeStateIntegrityService;
import com.enterprise.licensing.model.LicenseReasonCode;
import com.enterprise.licensing.engine.recovery.RecoveryService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Keeps a licensing decision, its mutation, and the audit filter's append in one transaction.
 */
@Component
@Order(0)
public final class TransactionBoundaryFilter extends OncePerRequestFilter {
    private final SqliteImmediateTransactionRunner transactions;
    private final AuthoritativeStateIntegrityService integrity;
    private final RecoveryService recovery;

    public TransactionBoundaryFilter(SqliteImmediateTransactionRunner transactions,
                                     AuthoritativeStateIntegrityService integrity, RecoveryService recovery) {
        this.transactions = transactions;
        this.integrity = integrity;
        this.recovery = recovery;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/license");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        LicenseReasonCode integrityResult = integrity.verifyOrInitialize();
        if (integrityResult != LicenseReasonCode.LICENSE_VALID) {
            recovery.observeValidationFailure(integrityResult);
            response.setStatus(503);
            response.setContentType("application/json");
            response.getWriter().write("{\"success\":false,\"reasonCode\":\"STATE_INTEGRITY_FAILURE\"}");
            return;
        }
        integrity.beginVerifiedRequest();
        try {
            if (request.getRequestURI().equals("/api/v1/license/admin/storage/backup")) {
                chain.doFilter(request, response);
                integrity.seal();
                return;
            }
            transactions.write(() -> {
                try {
                    chain.doFilter(request, response);
                    return null;
                } catch (ServletException | IOException exception) {
                    throw new FilterFailure(exception);
                }
            });
            integrity.seal();
        } catch (FilterFailure failure) {
            if (failure.getCause() instanceof ServletException servletException) throw servletException;
            if (failure.getCause() instanceof IOException ioException) throw ioException;
            throw failure;
        } finally {
            integrity.endVerifiedRequest();
        }
    }

    private static final class FilterFailure extends RuntimeException {
        private FilterFailure(Exception cause) {
            super(cause);
        }
    }
}
