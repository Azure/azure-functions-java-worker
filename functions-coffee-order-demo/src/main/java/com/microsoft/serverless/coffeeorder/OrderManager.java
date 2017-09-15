package com.microsoft.serverless.coffeeorder;

import java.util.*;
import com.microsoft.azure.serverless.functions.*;
import com.microsoft.azure.serverless.functions.annotation.*;

/**
 * The OrderManager class deals with user orders, and exposes public HTTP interface to the public.
 */
public class OrderManager {

    /**
     * The menu item of the getMenu() result.
     */
    private static class MenuItem {
        MenuItem(String name, int remaining) {
            this.name = name;
            this.remaining = remaining;
        }
        private String name;
        private int remaining;
    }

    /**
     * Get the available options for the user to choose.
     * @param coffeeItems The supported coffee types in the inventory table.
     * @return The list of available menu items.
     */
    public static MenuItem[] getMenu(@BindingName("inventory") InventoryEntry[] coffeeItems) {
        return Arrays.stream(coffeeItems)
                .filter(c -> c.getAmount() > 0)
                .map(c -> new MenuItem(c.getName(), c.getAmount()))
                .toArray(MenuItem[]::new);
    }

    /**
     * The place-order request from user.
     */
    private static class OrderRequest {
        String getCoffee() { return this.coffee; }
        int getAmount() { return this.amount; }

        private String coffee;
        private int amount;
    }

    /**
     * Place a new order.
     * @return The new created order ID if we have enough ingredients; otherwise report error.
     */
    public static HttpResponseMessage place(
            HttpRequestMessage placeholder,
            @BindingName("req") OrderRequest request,
            @BindingName("inventory") InventoryEntry coffee,
            @BindingName("order") OutputBinding<OrderEntry> newOrder,
            @BindingName("pending") OutputBinding<String> pendingOrderId) {

        HttpResponseMessage response = placeholder.createResponse();
        if (coffee == null || coffee.getName() == null) {
            response.setStatus(400);
            response.setBody("Coffee name is required");
            return response;
        }
        assert request.getCoffee().equals(coffee.getName());
        if (coffee.getAmount() < request.getAmount()) {
            response.setStatus(404);
            response.setBody("Coffee \"" + coffee.getName() + "\" has been sold out");
            return response;
        }

        OrderEntry order = new OrderEntry(coffee.getName(), request.getAmount());
        newOrder.setValue(order);
        pendingOrderId.setValue(order.getId());

        response.setStatus(201);
        response.setBody(order.getId());
        return response;
    }

    /**
     * Get status of an existing order.
     * @param order The existing order created by place() method.
     * @return The current status of the specified order.
     */
    public static String getStatus(@BindingName("order") OrderEntry order) {
        return (order == null ? null : order.getStatus().toString());
    }

}
