#include "precomp.h"

static order_t orders[ORDERS_CAPACITY];
static size_t head = 0, tail = 0;

void orders_initialize()
{
}

size_t orders_length()
{
    return (tail + ORDERS_CAPACITY - head) % ORDERS_CAPACITY;
}

bool orders_put(const char *message)
{
    if (orders_length() == ORDERS_CAPACITY - 1)
    {
        LogError("Failed to put more orders because the queue is full");
        return false;
    }

    if (!order_from_json(message, orders[tail++]))
    {
        --tail;
        return false;
    }
    tail %= ORDERS_CAPACITY;
    
    if (orders_length() == ORDERS_WARNING_CAPACITY)
    {
        // WARNING
    }
    return true;
}

bool orders_get(order_t &result)
{
    if (tail != head)
    {
        result = orders[head++];
        head %= ORDERS_CAPACITY;
        return true;
    }
    return false;
}

bool order_from_json(const char *message, order_t &order)
{
    StaticJsonBuffer<MESSAGE_MAX_LENGTH> jsonBuffer;
    JsonObject &root = jsonBuffer.parseObject(message);
    if (!root.success())
    {
        LogError("Failed to parse message as JSON object");
        return false;
    }

    const char *id = root["id"];
    const char *coffee = root["coffee"];
    int amount = root["amount"];

    if (id == nullptr || strlen(id) == 0)
    {
        LogError("Failed to parse message because id is empty");
        return false;
    }
    if (coffee == nullptr || strlen(coffee) == 0)
    {
        LogError("Failed to parse message because coffee name is empty");
        return false;
    }
    if (amount <= 0)
    {
        LogError("Failed to parse message because amount is illegal");
        return false;
    }

    strcpy(order.id, id);
    strcpy(order.name, coffee);
    order.amount = amount;

    LogInfo("Message parsed to Order { \"%s\", \"%s\", %d }", order.id, order.name, order.amount);
    return true;
}

const char *order_to_json(const order_t &order, bool accepted)
{
    static char jsonString[MESSAGE_MAX_LENGTH];
    StaticJsonBuffer<MESSAGE_MAX_LENGTH> jsonBuffer;
    JsonObject &root = jsonBuffer.createObject();
    root["id"] = order.id;
    root["accepted"] = accepted;
    auto written = root.printTo(jsonString, MESSAGE_MAX_LENGTH);
    if (written == 0)
    {
        LogError("Failed to convert Order { \"%s\", \"%s\",  %d } to json", order.id, order.name, order.amount);
    }
    LogInfo("Order converted to json \"%s\"", jsonString);
    return written > 0 ? jsonString : nullptr;
}
