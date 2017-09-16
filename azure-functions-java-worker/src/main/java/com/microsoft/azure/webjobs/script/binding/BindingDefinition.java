package com.microsoft.azure.webjobs.script.binding;

import com.microsoft.azure.webjobs.script.rpc.messages.*;

public final class BindingDefinition {
    public BindingDefinition(String name, BindingInfo info) {
        this.name = name;
        this.direction = info.getDirection();
        this.bindingType = BindingType.parse(info.getType());
    }

    String getName() { return this.name; }
    BindingType getBindingType() { return this.bindingType; }
    boolean isInput() { return this.direction == BindingInfo.Direction.in || this.direction == BindingInfo.Direction.inout; }
    boolean isOutput() { return this.direction == BindingInfo.Direction.out || this.direction == BindingInfo.Direction.inout; }

    enum BindingType {
        TIMER, HTTP, QUEUE, TABLE, BLOB, SERVICEBUS, EVENTHUB, NOTIFICATIONHUB, MANUAL, MOBILETABLE, DOCUMENTDB, TWILIOSMS, SENDGRID, UNKNOWN;

        private static BindingType parse(String type) {
            switch (type) {
                case "timerTrigger": return TIMER;
                case "http": case "httpTrigger": return HTTP;
                case "queue": case "queueTrigger": return QUEUE;
                case "table": return TABLE;
                case "blob": case "blobTrigger": return BLOB;
                case "serviceBus": case "serviceBusTrigger": return SERVICEBUS;
                case "eventHub": case "eventHubTrigger": return EVENTHUB;
                case "notificationHub": return NOTIFICATIONHUB;
                case "manualTrigger": return MANUAL;
                case "mobileTable": return MOBILETABLE;
                case "documentDB": return DOCUMENTDB;
                case "twilioSms": return TWILIOSMS;
                case "sendGrid": return SENDGRID;
                default: return UNKNOWN;
            }
        }
    }

    private final String name;
    private final BindingInfo.Direction direction;
    private final BindingType bindingType;
}
