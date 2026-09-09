package com.orqentra.auth.identity;

/**
 * Raised for both an unknown email and a wrong password, deliberately carrying the same
 * message either way so the response cannot be used to discover which emails exist.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
