#ifndef IOTHUB_H
#define IOTHUB_H

bool iothub_initialize();
void iothub_synchronize();

const char *iothub_recv();
bool iothub_send(const char *);

bool iothub_is_sending();
bool iothub_last_sent();

#endif /* IOTHUB_H */
