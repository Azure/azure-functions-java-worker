package com.microsoft.azure.functions.worker.binding;

import com.microsoft.azure.functions.RetryContext;
import com.microsoft.azure.functions.RpcException;

final public class ExecutionRetryContext implements RetryContext {
    public ExecutionRetryContext(int retryCount, int maxRetryCount, RpcException rpcException) {
        this.Retrycount = retryCount;
        this.Maxretrycount = maxRetryCount;
        this.Rpcexception = rpcException;
    }

    public ExecutionRetryContext(int retryCount, int maxRetryCount, com.microsoft.azure.functions.rpc.messages.RpcException exception) {
        this.Retrycount = retryCount;
        this.Maxretrycount = maxRetryCount;
        this.Rpcexception = new RpcException() {
            @Override
            public String getSource() {
                return exception.getSource();
            }

            @Override
            public String getStacktrace() {
                return exception.getStackTrace();
            }

            @Override
            public String getMessage() {
                return exception.getMessage();
            }
        };
    }

    @Override
    public int getRetrycount() {
        return Retrycount;
    }

    @Override
    public int getMaxretrycount() {
        return Maxretrycount;
    }

    @Override
    public RpcException getException() {
        return Rpcexception;
    }

    private final int Retrycount;
    private final int Maxretrycount;
    private final RpcException Rpcexception;

}