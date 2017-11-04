package com.microsoft.azure.webjobs.script.handler;

import java.util.*;
import java.util.logging.*;

import org.apache.commons.lang3.exception.*;

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
        Optional.ofNullable(invocationId).ifPresent(log::setInvocationId);
        Optional.ofNullable(record.getLoggerName()).ifPresent(log::setCategory);
        Optional.ofNullable(mapLogLevel(record.getLevel())).ifPresent(log::setLevel);
        Optional.ofNullable(record.getMessage()).ifPresent(log::setMessage);
        Optional.ofNullable(mapException(record.getThrown())).ifPresent(log::setException);
        return log;
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
        Optional.ofNullable(t.getMessage()).ifPresent(exception::setMessage);
        exception.setStackTrace(ExceptionUtils.getStackTrace(t));
        return exception;
    }
}
