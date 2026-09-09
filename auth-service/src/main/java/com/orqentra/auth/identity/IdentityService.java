package com.orqentra.auth.identity;

import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityService {

    /**
     * A BCrypt hash of a value nobody knows. When the email is unknown there is no stored
     * hash to check, so the encoder is run against this instead: the work done, and
     * therefore the time taken, matches the wrong-password path. Without it, an unknown
     * email would return noticeably faster and leak which accounts exist.
     */
    private static final String ABSENT_USER_HASH =
            "$2a$10$m4Ow.dWDv7surMrYnN6/N.zi4x6cNta9p87v.hYEgrQbGQCsQMzLO";

    private final UserRepository users;
    private final RestaurantRepository restaurants;
    private final PasswordEncoder passwordEncoder;

    public IdentityService(UserRepository users,
                           RestaurantRepository restaurants,
                           PasswordEncoder passwordEncoder) {
        this.users = users;
        this.restaurants = restaurants;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public AuthDtos.RegisterResponse register(AuthDtos.RegisterRequest request) {
        if (request.email() == null || request.email().isBlank()) {
            throw new IllegalArgumentException("email is required");
        }
        if (request.password() == null || request.password().isBlank()) {
            throw new IllegalArgumentException("password is required");
        }
        if (request.restaurantName() == null || request.restaurantName().isBlank()) {
            throw new IllegalArgumentException("restaurantName is required");
        }

        String email = request.email().trim().toLowerCase();
        if (users.existsByEmail(email)) {
            throw new DuplicateEmailException(email);
        }

        Restaurant restaurant = restaurants.save(
                new Restaurant(UUID.randomUUID().toString(), request.restaurantName()));

        User user = users.save(new User(
                email,
                passwordEncoder.encode(request.password()),
                restaurant,
                Role.RESTAURANT));

        return new AuthDtos.RegisterResponse(user.getEmail(), restaurant.getReference());
    }

    /**
     * Verifies credentials. Both failure modes raise the same exception, and the hash
     * comparison runs even when no user was found, so neither the body nor the timing
     * distinguishes an unknown email from a wrong password.
     */
    @Transactional(readOnly = true)
    public User authenticate(String rawEmail, String rawPassword) {
        String email = rawEmail == null ? "" : rawEmail.trim().toLowerCase();
        String password = rawPassword == null ? "" : rawPassword;

        User user = users.findByEmail(email).orElse(null);
        String hash = user == null ? ABSENT_USER_HASH : user.getPasswordHash();

        boolean matches = passwordEncoder.matches(password, hash);
        if (user == null || !matches) {
            throw new InvalidCredentialsException();
        }
        return user;
    }

    @Transactional(readOnly = true)
    public User requireByEmail(String email) {
        return users.findByEmail(email).orElseThrow(InvalidCredentialsException::new);
    }
}
