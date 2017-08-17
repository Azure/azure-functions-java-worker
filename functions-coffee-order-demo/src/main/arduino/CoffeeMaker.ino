#include "precomp.h"

static bool initialized = false;

static bool hasOrder = false, trackingSend = false, needRetry = false, accepted;
static order_t currentOrder;

void setup()
{
    hardware_initialize();
    orders_initialize();
    if (wifi_initialize())
    {
        Serial.begin(115200);
        initialized = iothub_initialize();
    }
    else
    {
        LogInfo("Please make sure the wifi connected!");
    }
}

static void display_hint()
{
    Screen.print(0, "Orders Queue    ", false);
    Screen.print(1, "   Get Order -->", false);
    Screen.print(2, "                ", false);
    auto len = orders_length();
    if (len > 0)
    {
        char buffer[LINE_MAX_LENGTH];
        snprintf(buffer, LINE_MAX_LENGTH, "%d Waiting...    ", orders_length());
        Screen.print(3, buffer, false);
    }
    else
    {
        Screen.print(3, "No Orders :)    ", false);
    }
}

static void display_detail()
{
    Screen.print(0, "Order Detail    ", false);
    Screen.print(1, "<-- X      ! -->", false);
    char buffer[LINE_MAX_LENGTH];
    snprintf(buffer, LINE_MAX_LENGTH, "%d %s               ", currentOrder.amount, currentOrder.name);
    Screen.print(2, buffer, false);
    Screen.print(3, "                ", false);
}

static void display_sending()
{
    Screen.print(3, (accepted ? "  Accepting...  " : "   Denying...   "), false);
}

static void display_retry()
{
    Screen.print(3, ":(, need retry  ", false);
}

static void update_ui()
{
    if (!hasOrder)
    {
        display_hint();
    }
    else
    {
        display_detail();
        if (trackingSend)
        {
            display_sending();
        }
        else
        {
            if (needRetry)
            {
                display_retry();
            }
        }
    }
}

void loop()
{
    if (initialized)
    {
        iothub_synchronize();
        if (trackingSend)
        {
            if (!iothub_is_sending())
            {
                if (iothub_last_sent())
                {
                    hasOrder = false;
                }
                else
                {
                    needRetry = true;
                }
                trackingSend = false;
            }
        }
        if (auto message = iothub_recv())
        {
            if (orders_put(message))
            {
                LogInfo("Message \"%s\" has been put into orders queue", message);
            }
        }
        if (!hasOrder)
        {
            if (is_button_B_clicked())
            {
                hasOrder = orders_get(currentOrder);
            }
        }
        else
        {
            bool clicked = false;
            if (is_button_A_clicked())
            {
                accepted = false;
                clicked = true;
            }
            if (is_button_B_clicked())
            {
                accepted = true;
                clicked = true;
            }
            if (clicked)
            {
                if (auto message = order_to_json(currentOrder, accepted))
                {
                    if (iothub_send(message))
                    {
                        needRetry = false;
                        trackingSend = true;
                    }
                    else
                    {
                        needRetry = true;
                    }
                }
                else
                {
                    needRetry = true;
                }
            }
        }
        update_ui();
    }
    delay(10);
}
