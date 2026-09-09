package com.orqentra.payment.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Renders a missing, malformed or expired token as a ProblemDetail, matching the error
 * shape the rest of the API already uses instead of the container's HTML error page.
 */
public class ProblemDetailAuthEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        ProblemDetailWriter.write(request, response, HttpStatus.UNAUTHORIZED,
                "Unauthorized", "A valid bearer token is required");
    }
}
