package com.microsoft.serverless.coffeeorder;

import com.microsoft.azure.storage.table.TableServiceEntity;

public class OrderTableEntity extends TableServiceEntity {
    public String getStatus() { return this.status; }
    public void setStatus(String status) { this.status = status; }

    public String getCoffee() { return this.coffee; }
    public void setCoffee(String coffee) { this.coffee = coffee; }

    public int getAmount() { return this.amount; }
    public void setAmount(int amount) { this.amount = amount; }

    private String status;
    private String coffee;
    private int amount;
}
