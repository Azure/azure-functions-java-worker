package com.microsoft.azure.webjobs.script;

import java.util.*;
import java.util.logging.*;

public class HostLoggingListener extends Handler {
    private HostLoggingListener(JavaWorkerClient.StreamingMessagePeer peer) {
        assert instance == null;
        this.closed = false;
        this.peer = peer;
        this.invocationSessions = new Stack<>();
        Application.LOGGER.addHandler(this);
    }

    @Override
    public void close() throws SecurityException {
        Application.LOGGER.removeHandler(this);
        this.closed = true;
    }

    static void newInstance(JavaWorkerClient.StreamingMessagePeer peer) {
        assert instance == null;
        instance = new HostLoggingListener(peer);
    }

    static void releaseInstance() {
        if (instance != null) {
            instance.close();
            instance = null;
        }
    }

    public static Optional<HostLoggingListener> getInstance() {
        return Optional.ofNullable(instance);
    }

    public void pushInvocation(String invocationId) {
        this.invocationSessions.push(invocationId);
    }

    public void popInvocation() {
        if (!this.invocationSessions.empty()) {
            this.invocationSessions.pop();
        }
    }

    @Override
    public void publish(LogRecord record) {
        if (!this.closed) {
            try {
                // Prevent unbounded recursive calls
                Application.LOGGER.removeHandler(this);
                this.peer.log(record, this.invocationSessions.empty() ? null : this.invocationSessions.peek());
                Application.LOGGER.addHandler(this);
            } catch (Exception ex) {
                this.close();
                throw ex;
            }
        }
    }

    @Override
    public void flush() { }

    private boolean closed;
    private JavaWorkerClient.StreamingMessagePeer peer;
    private Stack<String> invocationSessions;
    private static HostLoggingListener instance = null;
}
