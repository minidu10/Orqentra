package com.orqentra.auth.identity;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.orqentra.auth.security.TokenIssuer;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final IdentityService identity;
    private final TokenIssuer tokens;

    public AuthController(IdentityService identity, TokenIssuer tokens) {
        this.identity = identity;
        this.tokens = tokens;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthDtos.RegisterResponse register(@RequestBody AuthDtos.RegisterRequest request) {
        return identity.register(request);
    }

    @PostMapping("/login")
    public AuthDtos.LoginResponse login(@RequestBody AuthDtos.LoginRequest request) {
        User user = identity.authenticate(request.email(), request.password());
        return tokens.issue(user);
    }

    @GetMapping("/me")
    public AuthDtos.MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        User user = identity.requireByEmail(jwt.getSubject());
        return new AuthDtos.MeResponse(
                user.getEmail(),
                user.getRestaurant().getReference(),
                user.getRole().name());
    }

    @ExceptionHandler(DuplicateEmailException.class)
    public ProblemDetail duplicateEmail(DuplicateEmailException ex) {
        return problem(HttpStatus.CONFLICT, "Email already registered", ex.getMessage());
    }

    /**
     * One response for both an unknown email and a wrong password. Anything that differed
     * between the two would turn this endpoint into an account enumeration oracle.
     */
    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail invalidCredentials(InvalidCredentialsException ex) {
        return problem(HttpStatus.UNAUTHORIZED, "Invalid credentials", ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail badRequest(IllegalArgumentException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", ex.getMessage());
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setTitle(title);
        return body;
    }
}
