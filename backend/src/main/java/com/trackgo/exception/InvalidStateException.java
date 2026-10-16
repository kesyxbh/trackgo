package com.trackgo.exception;

/** The request is well-formed but not allowed in the current state, e.g. an illegal status jump (HTTP 409). */
public class InvalidStateException extends RuntimeException {

    public InvalidStateException(String message) {
        super(message);
    }
}
