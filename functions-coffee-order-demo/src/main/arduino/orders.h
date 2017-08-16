#ifndef ORDERS_H
#define ORDERS_H

struct order_t
{
    char id[ORDER_STRING_MAX_LENGTH];
    char name[ORDER_STRING_MAX_LENGTH];
    unsigned int amount;
};

void orders_initialize();
size_t orders_length();
bool orders_put(const char *message);
bool orders_get(order_t &result);

bool order_from_json(const char *, order_t &);
const char *order_to_json(const order_t &, bool);

#endif /* ORDERS_H */
