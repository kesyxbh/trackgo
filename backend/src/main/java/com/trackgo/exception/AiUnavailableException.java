package com.trackgo.exception;

/** The language model could not be reached or failed while answering (HTTP 502). */
public class AiUnavailableException extends RuntimeException {

    public AiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
