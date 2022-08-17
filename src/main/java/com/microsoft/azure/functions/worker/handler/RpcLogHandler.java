package com.microsoft.azure.functions.worker.handler;

import java.util.*;
import java.util.logging.*;

import org.apache.commons.lang3.exception.*;

import com.microsoft.azure.functions.worker.*;
import com.microsoft.azure.functions.rpc.messages.*;

public class RpcLogHandler extends OutboundMessageHandler<RpcLog.Builder> {
    public RpcLogHandler(LogRecord record, String invocationId) {
        super(() -> generateRpcLog(record, invocationId), StreamingMessage.Builder::setRpcLog);
    }

    @Override
    Logger getLogger() { return WorkerLogManager.getEmptyLogger(); }

    private static RpcLog.Builder generateRpcLog(LogRecord record, String invocationId) {
        RpcLog.Builder log = RpcLog.newBuilder();
        /**
         * Check if the logging namespace belongs to system logsq, invocation log should be categorized to user type (default), others should
         * be categorized to system type.
         *
         *              local_console   customer_app_insight    functions_kusto_table
         * system_log   false,           false,                   true
         * user_log     true,            true,                    false
         */
        if (invocationId == null){
            log.setLogCategory(RpcLog.RpcLogCategory.System);
            log.setCategory(WorkerLogManager.SYSTEM_LOG_PREFIX);
        }
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
