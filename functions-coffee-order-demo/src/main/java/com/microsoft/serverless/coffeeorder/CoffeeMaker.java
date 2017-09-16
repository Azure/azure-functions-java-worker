package com.microsoft.serverless.coffeeorder;

import com.microsoft.azure.serverless.functions.annotation.*;

/**
 * The CoffeeMaker class is used to send notifications to the actual coffee machine, and receive notifications from it.
 */
public class CoffeeMaker {
    /**
     * Prepare for the specific coffee as the order requested. It will also update the order status and the inventory data.
     */
    public static void prepare(OrderEntry order) {
        try (CoffeeMachine machine = new CoffeeMachine()) {
            machine.queueOrder(order);
            order.setStatus(OrderStatus.Preparing);
        } catch (Exception ex) {
            ex.printStackTrace();
            order.setStatus(OrderStatus.Cancelled);
        }
        try {
            new OrderTable().update(order);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Update order status as well as the inventory data when a coffee is made.
     */
    public static void onResponse(@BindingName("response") CoffeeMachineResponse response,
                                  @BindingName("order") OrderEntry order,
                                  @BindingName("inventory") InventoryEntry inventory) {

        try  {
            order.setStatus(response.isAccepted() ? OrderStatus.Making : OrderStatus.Cancelled);
            new OrderTable().update(order);
            if (response.isAccepted()) {
                inventory.decreseAmount(order.getAmount());
                new InventoryTable().update(inventory);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
