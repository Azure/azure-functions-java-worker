package com.microsoft.azure.functions.worker.exception;

public class FunctionExecutionException extends FunctionBaseException{
    public FunctionExecutionException(){
        super();
    }

    public FunctionExecutionException(String message) {
        super(message);
    }

    public FunctionExecutionException(String message, Throwable cause) {
        super(message, cause);
    }

    public FunctionExecutionException(Throwable cause) {
        super(cause);
    }
}
