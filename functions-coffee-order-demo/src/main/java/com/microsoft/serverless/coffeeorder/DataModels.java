package com.microsoft.serverless.coffeeorder;

import com.microsoft.azure.sdk.iot.service.*;
import com.microsoft.azure.sdk.iot.service.ServiceClient;
import com.microsoft.azure.sdk.iot.service.exceptions.*;
import com.microsoft.azure.storage.*;
import com.microsoft.azure.storage.table.*;

import java.io.*;
import java.net.*;
import java.security.*;
import java.util.*;

class InventoryEntry {
    String getName() { return this.RowKey; }
    int getAmount() { return this.Amount; }
    void decreseAmount(int amount) { this.Amount -= amount; assert this.Amount >= 0; }

    private String RowKey;
    private int Amount;
}

enum OrderStatus {
    NewOrder,
    Preparing,
    Making,
    Cancelled
}

class OrderEntry {
    OrderEntry(String coffee, int amount) {
        assert coffee != null && amount > 0;
        this.PartitionKey = "CustomerOrder";
        this.RowKey = UUID.randomUUID().toString();
        this.setStatus(OrderStatus.NewOrder);
        this.Coffee = coffee;
        this.Amount = amount;
    }

    String getId() { return this.RowKey; }
    OrderStatus getStatus() { return this.Status; }
    void setStatus(OrderStatus status) { this.Status = status; }
    String getCoffee() { return this.Coffee; }
    int getAmount() { return this.Amount; }

    private String PartitionKey;
    private String RowKey;
    private OrderStatus Status;
    private String Coffee;
    private int Amount;
}

abstract class EntityTable<U, V extends TableServiceEntity> {
    EntityTable(String tableName) throws URISyntaxException, InvalidKeyException, StorageException {
        CloudStorageAccount account = CloudStorageAccount.parse(CONNECTION_STRING);
        CloudTableClient client = account.createCloudTableClient();
        this.table = client.getTableReference(tableName);
    }

    void update(U item) throws StorageException {
        TableOperation retrieving = this.getRetrievingOperation(item);
        V entity = this.table.execute(retrieving).getResultAsType();
        this.mapItemToEntity(item, entity);
        TableOperation replacing = TableOperation.replace(entity);
        this.table.execute(replacing);
    }

    abstract TableOperation getRetrievingOperation(U item);
    abstract void mapItemToEntity(U item, V entity);

    private CloudTable table;
    private static final String CONNECTION_STRING = "DefaultEndpointsProtocol=https;AccountName=funccoffeemaker;AccountKey=xAPzVNkT45eFD8q7surtTMlHb+R3qPCZAfeoHVSZ7jOMqyft+swduT/XI064oO68JdQ79TxMK8Dv6Y/RZaE/uQ==";
}

class InventoryTable extends EntityTable<InventoryEntry, InventoryTableEntity> {
    InventoryTable() throws URISyntaxException, InvalidKeyException, StorageException {
        super("inventory");
    }

    @Override
    TableOperation getRetrievingOperation(InventoryEntry item) {
        return TableOperation.retrieve("Remaining", item.getName(), InventoryTableEntity.class);
    }

    @Override
    void mapItemToEntity(InventoryEntry item, InventoryTableEntity entity) {
        entity.setAmount(item.getAmount());
    }
}

class OrderTable extends EntityTable<OrderEntry, OrderTableEntity> {
    OrderTable() throws URISyntaxException, InvalidKeyException, StorageException {
        super("orders");
    }

    @Override
    TableOperation getRetrievingOperation(OrderEntry item) {
        return TableOperation.retrieve("CustomerOrder", item.getId(), OrderTableEntity.class);
    }

    @Override
    void mapItemToEntity(OrderEntry item, OrderTableEntity entity) {
        entity.setStatus(item.getStatus().toString());
    }
}

class CoffeeMachineResponse {
    public boolean isAccepted() { return this.accepted; }
    private boolean accepted;
}

class CoffeeMachine implements AutoCloseable {
    CoffeeMachine() throws IOException {
        this.client = ServiceClient.createFromConnectionString(CONNECTION_STRING, MESSAGE_PROTOCOL);
        this.client.open();
    }

    @Override
    public void close() throws Exception {
        if (this.client != null) {
            this.client.close();
        }
    }

    void queueOrder(OrderEntry order) throws IOException, IotHubException {
        String content = String.format(
                "{ \"id\": \"%s\", \"coffee\": \"%s\", \"amount\": %d }",
                order.getId(), order.getCoffee(), order.getAmount());
        this.client.send(DEVICE_ID, new Message(content));
    }

    private ServiceClient client = null;

    private static final String CONNECTION_STRING = "HostName=funccoffeemaker.azure-devices.net;SharedAccessKeyName=iothubowner;SharedAccessKey=qehYAmgSR38V57ai0IDUQQWGIxIubGbMAwrE19JUtVw=";
    private static final String DEVICE_ID = "AZ3166";
    private static final IotHubServiceClientProtocol MESSAGE_PROTOCOL = IotHubServiceClientProtocol.AMQPS;
}
