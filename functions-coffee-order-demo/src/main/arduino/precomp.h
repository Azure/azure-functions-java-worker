#ifndef PRECOMP_H
#define PRECOMP_H

#include "Arduino.h"
#include "EEPROMInterface.h"
#include "AZ3166WiFi.h"
#include "HTS221Sensor.h"
#include "RGB_LED.h"
#include <ArduinoJson.h>
#include "AzureIotHub.h"
#include "iothub_client_ll.h"


#define ORDER_STRING_MAX_LENGTH 128

#define ORDERS_CAPACITY 64
#define ORDERS_WARNING_CAPACITY 48

#define MESSAGE_MAX_LENGTH 1024

#define LINE_MAX_LENGTH 64

#define RGB_LED_BRIGHTNESS 32


#include "hardware.h"
#include "iothub.h"
#include "orders.h"


#endif /* PRECOMP_H */
