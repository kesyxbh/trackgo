package com.trackgo.exception;

/** The requested customer, rider or delivery does not exist (HTTP 404). */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
