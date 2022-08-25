package com.microsoft.azure.functions.worker.exception;

public class WorkerBaseException extends RuntimeException{
    public WorkerBaseException() {
        super();
    }

    public WorkerBaseException(String message) {
        super(message);
    }

    public WorkerBaseException(String message, Throwable cause) {
        super(message, cause);
    }

    public WorkerBaseException(Throwable cause) {
        super(cause);
    }

    public WorkerBaseException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
