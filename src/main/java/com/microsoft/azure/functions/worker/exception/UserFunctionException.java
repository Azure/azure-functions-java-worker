package com.microsoft.azure.functions.worker.exception;

public class UserFunctionException extends WorkerBaseException{

    private static final String message = "exception happens in customer function: ";
    public UserFunctionException() {
        super();
    }

    public UserFunctionException(String message) {
        super(message);
    }

    public UserFunctionException(String input, Throwable cause) {
        super(message + input, cause);
    }

    public UserFunctionException(Throwable cause) {
        super(cause);
    }

    public UserFunctionException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
