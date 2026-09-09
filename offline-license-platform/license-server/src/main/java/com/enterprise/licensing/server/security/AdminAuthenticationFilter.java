package com.enterprise.licensing.server.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@org.springframework.core.annotation.Order(10)
@Component
public final class AdminAuthenticationFilter extends OncePerRequestFilter {
    private final byte[] expected;

    public AdminAuthenticationFilter(@Value("${licensing.admin-token:}") String token) {
        expected = token.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest r) {
        return !r.getRequestURI().startsWith("/api/v1/license/admin");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        byte[] provided = header != null && header.startsWith("Bearer ") ? header.substring(7).getBytes(StandardCharsets.UTF_8) : new byte[0];
        if (expected.length == 0 || !MessageDigest.isEqual(expected, provided)) {
            response.setStatus(401);
            response.setContentType("application/json");
            response.getWriter().write("{\"success\":false,\"reasonCode\":\"UNAUTHORIZED\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
