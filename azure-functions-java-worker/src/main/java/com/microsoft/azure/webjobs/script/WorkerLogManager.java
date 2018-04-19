package com.microsoft.azure.webjobs.script;

import java.io.*;
import java.util.logging.*;

import org.apache.commons.lang3.*;
import org.apache.commons.lang3.exception.*;

public class WorkerLogManager {
    public static Logger getEmptyLogger() { return INSTANCE.emptyLogger; }
    public static Logger getSystemLogger() { return INSTANCE.systemLogger; }
    public static Logger getHostLogger() { return INSTANCE.hostLogger; }
    public static Logger getInvocationLogger(String invocationId) { return INSTANCE.getInvocationLoggerImpl(invocationId); }

    static void initialize(JavaWorkerClient client, boolean logToConsole) { INSTANCE.initializeImpl(client, logToConsole); }
    static void deinitialize() { INSTANCE.deinitializempl(); }

    private WorkerLogManager() {
        this.client = null;
        clearHandlers(this.emptyLogger = Logger.getAnonymousLogger());
        addSystemHandler(this.systemLogger = Logger.getAnonymousLogger());
        addSystemHandler(this.hostLogger = Logger.getAnonymousLogger());
        this.systemLogger.setLevel(Level.ALL);
        this.hostLogger.setLevel(Level.ALL);
    }

    private void initializeImpl(JavaWorkerClient client, boolean logToConsole) {
        assert this.client == null && client != null;
        this.client = client;
        this.logToConsole = logToConsole;
        addHostClientHandlers(this.hostLogger, null);
    }

    private void deinitializempl() {
        assert this.client != null;
        clearHandlers(this.hostLogger);
        this.logToConsole = false;
        this.client = null;
    }

    private Logger getInvocationLoggerImpl(String invocationId) {
        Logger logger = Logger.getAnonymousLogger();
        logger.setLevel(Level.ALL);
        addHostClientHandlers(logger, invocationId);
        return logger;
    }

    private static void clearHandlers(Logger logger) {
        logger.setUseParentHandlers(false);
        Handler[] handlers = logger.getHandlers();
        for (Handler handler : handlers) {
            logger.removeHandler(handler);
        }
    }

    private static void addSystemHandler(Logger logger) {
        clearHandlers(logger);
        logger.addHandler(new SystemLoggerListener());
    }

    private void addHostClientHandlers(Logger logger, String invocationId) {
        assert this.client != null;
        clearHandlers(logger);
        logger.addHandler(new HostLoggerListener(this.client, invocationId));
        if (this.logToConsole) { logger.addHandler(new SystemLoggerListener()); }
    }

    private JavaWorkerClient client;
    private boolean logToConsole;
    private final Logger emptyLogger, systemLogger, hostLogger;
    private final static WorkerLogManager INSTANCE = new WorkerLogManager();
}

class SystemLoggerListener extends Handler {
    @Override
    public void publish(LogRecord record) {
        if (record != null && record.getLevel() != null) {
            PrintStream output = (record.getLevel().intValue() <= Level.INFO.intValue() ? System.out : System.err);
            output.println(String.format("[%s] {%s.%s}: %s",
                    record.getLevel(),
                    ClassUtils.getShortClassName(record.getSourceClassName()), record.getSourceMethodName(),
                    record.getMessage()));
            if (record.getThrown() != null) {
                output.println(String.format("%s", ExceptionUtils.getStackTrace(record.getThrown())));
            }
        }
    }

    @Override
    public void flush() {
        System.out.flush();
        System.err.flush();
    }

    @Override
    public void close() throws SecurityException { }
}

class HostLoggerListener extends Handler {
    HostLoggerListener(JavaWorkerClient client, String invocationId) {
        assert client != null;
        this.client = client;
        this.invocationId = invocationId;
    }

    @Override
    public void publish(LogRecord record) {
        this.client.logToHost(record, this.invocationId);
    }

    @Override
    public void flush() { }

    @Override
    public void close() throws SecurityException { }

    private final JavaWorkerClient client;
    private final String invocationId;
}
