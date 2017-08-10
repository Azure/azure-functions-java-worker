package com.microsoft.azure.webjobs.script.handler;

import java.util.function.*;
import java.util.logging.*;

import com.microsoft.azure.webjobs.script.*;
import com.microsoft.azure.webjobs.script.rpc.messages.*;

public class RpcLogHandler extends OutboundMessageHandler<RpcLog.Builder> {
    public RpcLogHandler(LogRecord record, String invocationId) {
        super(() -> generateRpcLog(record, invocationId), StreamingMessage.Builder::setRpcLog);
    }

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
        if (Level.FINEST.equals(level) || Level.FINER.equals(level)) {
            return RpcLog.Level.Trace;
        } else if (Level.FINE.equals(level) || Level.CONFIG.equals(level)) {
            return RpcLog.Level.Debug;
        } else if (Level.INFO.equals(level)) {
            return RpcLog.Level.Information;
        } else if (Level.WARNING.equals(level)) {
            return RpcLog.Level.Warning;
        } else if (Level.SEVERE.equals(level)) {
            return RpcLog.Level.Error;
        } else if (Application.LEVEL_CRITICAL.equals(level)) {
            return RpcLog.Level.Critical;
        }
        return null;
    }

    private static RpcException.Builder mapException(Throwable t) {
        if (t == null) { return null; }
        RpcException.Builder exception = RpcException.newBuilder();
        doIfNotNull(exception::setMessage, t.getMessage());
        doIfNotNull(exception::setStackTrace, Application.stackTraceToString(t));
        return exception;
    }
}
