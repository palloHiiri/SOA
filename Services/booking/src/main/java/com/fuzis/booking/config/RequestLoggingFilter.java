package com.fuzis.booking.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final int MAX_REQUEST_ID_LENGTH = 128;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = normalizeRequestId(request.getHeader("X-Request-ID"));
        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
        }

        MDC.put("requestId", requestId);
        response.setHeader("X-Request-ID", requestId);

        long startedAt = System.nanoTime();
        String query = request.getQueryString();
        String path = request.getRequestURI();

        log.info("HTTP request started: method={}, uri={}, query={}, remote={}",
                request.getMethod(),
                path,
                query == null ? "" : query,
                request.getRemoteAddr());

        try {
            filterChain.doFilter(request, response);
        } catch (Exception exception) {
            long elapsedMs = elapsedMs(startedAt);
            log.error("HTTP request failed: method={}, uri={}, elapsedMs={}, exception={}",
                    request.getMethod(), path, elapsedMs, exception.toString(), exception);
            throw exception;
        } finally {
            long elapsedMs = elapsedMs(startedAt);
            if (response.getStatus() >= 500) {
                log.error("HTTP request completed with server error: method={}, uri={}, status={}, elapsedMs={}",
                        request.getMethod(), path, response.getStatus(), elapsedMs);
            } else if (response.getStatus() >= 400) {
                log.warn("HTTP request completed with client error: method={}, uri={}, status={}, elapsedMs={}",
                        request.getMethod(), path, response.getStatus(), elapsedMs);
            } else {
                log.info("HTTP request completed: method={}, uri={}, status={}, elapsedMs={}",
                        request.getMethod(), path, response.getStatus(), elapsedMs);
            }
            MDC.remove("requestId");
        }
    }

    private static String normalizeRequestId(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return null;
        }
        String normalized = requestId.trim();
        if (normalized.length() > MAX_REQUEST_ID_LENGTH) {
            return normalized.substring(0, MAX_REQUEST_ID_LENGTH);
        }
        return normalized;
    }

    private static long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
