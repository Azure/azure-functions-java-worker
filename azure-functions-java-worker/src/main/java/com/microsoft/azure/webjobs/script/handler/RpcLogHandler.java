package com.microsoft.azure.webjobs.script.handler;

import java.util.function.*;
import java.util.logging.*;

import com.microsoft.azure.webjobs.script.*;
import com.microsoft.azure.webjobs.script.rpc.messages.*;

public class RpcLogHandler extends OutboundMessageHandler<RpcLog.Builder> {
    public RpcLogHandler(LogRecord record, String invocationId) {
        super(() -> generateRpcLog(record, invocationId), StreamingMessage.Builder::setRpcLog);
    }

    @Override
    Logger getLogger() { return WorkerLogManager.getEmptyLogger(); }

    private static RpcLog.Builder generateRpcLog(LogRecord record, String invocationId) {
        RpcLog.Builder log = RpcLog.newBuilder();
        doIfNotNull(log::setInvocationId, invocationId);
        doIfNotNull(log::setCategory, record.getLoggerName());
        doIfNotNull(log::setLevel, mapLogLevel(record.getLevel()));
        doIfNotNull(log::setMessage, record.getMessage());
        doIfNotNull(log::setException, mapException(record.getThrown()));
        return log;
    }

    private static <T> void doIfNotNull(Consumer<T> consumer, T input) {
        if (input != null) {
            consumer.accept(input);
        }
    }

    private static RpcLog.Level mapLogLevel(Level level) {
        if (level.intValue() <= Level.FINE.intValue()) {
            return RpcLog.Level.Trace;
        } else if (level.intValue() < Level.INFO.intValue()) {
            return RpcLog.Level.Debug;
        } else if (level.intValue() < Level.WARNING.intValue()) {
            return RpcLog.Level.Information;
        } else if (level.intValue() < Level.SEVERE.intValue()) {
            return RpcLog.Level.Warning;
        } else {
            return RpcLog.Level.Error;
        }
    }

    private static RpcException.Builder mapException(Throwable t) {
        if (t == null) { return null; }
        RpcException.Builder exception = RpcException.newBuilder();
        doIfNotNull(exception::setMessage, t.getMessage());
        doIfNotNull(exception::setStackTrace, Utility.stackTraceToString(t));
        return exception;
    }
}
