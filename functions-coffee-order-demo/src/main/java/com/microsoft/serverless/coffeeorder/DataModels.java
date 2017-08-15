package com.microsoft.serverless.coffeeorder;

import java.util.*;

class InventoryEntry {
    String getName() { return this.RowKey; }
    int getAmount() { return this.Amount; }
    void decreseAmount(int amount) { this.Amount -= amount; assert this.Amount >= 0; }

    private String PartitionKey;
    private String RowKey;
    private int Amount;
}

enum OrderStatus {
    NewOrder,
    Preparing,
    CoffeeMade
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
