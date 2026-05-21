package com.rosogisoft.service;

public class StarlarkFunctionException extends RuntimeException {
    public StarlarkFunctionException(String message) {
        super(message);
    }

    public StarlarkFunctionException(String message, Throwable cause) {
        super(message, cause);
    }
}
