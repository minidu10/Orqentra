package com.orqentra.order.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Writes the ProblemDetail body by hand. These run inside the security filter chain,
 * before any message converter is in play, so the JSON is produced directly.
 */
final class ProblemDetailWriter {

    static void write(HttpServletRequest request,
                      HttpServletResponse response,
                      HttpStatus status,
                      String title,
                      String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        String body = "{\"type\":\"about:blank\",\"title\":\"" + escape(title)
                + "\",\"status\":" + status.value()
                + ",\"detail\":\"" + escape(detail)
                + "\",\"instance\":\"" + escape(request.getRequestURI()) + "\"}";

        response.getWriter().write(body);
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private ProblemDetailWriter() {}
}
