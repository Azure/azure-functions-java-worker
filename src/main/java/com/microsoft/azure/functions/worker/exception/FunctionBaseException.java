package com.microsoft.azure.functions.worker.exception;

public class FunctionBaseException extends RuntimeException{
    public FunctionBaseException(){
        super();
    }

    public FunctionBaseException(String message) {
        super(message);
    }

    public FunctionBaseException(String message, Throwable cause) {
        super(message, cause);
    }

    public FunctionBaseException(Throwable cause) {
        super(cause);
    }
}
