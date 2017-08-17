package com.microsoft.serverless.coffeeorder;

import com.microsoft.azure.storage.table.TableServiceEntity;

public class InventoryTableEntity extends TableServiceEntity {
    public int getAmount() { return this.amount; }
    public void setAmount(int amount) { this.amount = amount; }
    private int amount;
}
