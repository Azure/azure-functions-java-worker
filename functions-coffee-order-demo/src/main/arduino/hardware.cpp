#include "precomp.h"

static DevI2C *i2c;
static HTS221Sensor *sensor;
static RGB_LED rgbLed;

static bool is_button_clicked(unsigned char, bool &);

void hardware_initialize()
{
    i2c = new DevI2C(D14, D15);
    sensor = new HTS221Sensor(*i2c);
    sensor->init(NULL);
}

bool wifi_initialize()
{
    Screen.print("IoT DevKit\r\n \r\nConnecting...\r\n");

    if (WiFi.begin() == WL_CONNECTED)
    {
        IPAddress ip = WiFi.localIP();
        Screen.print(1, ip.get_address());
        Screen.print(2, "Running... \r\n");
        return true;
    }
    else
    {
        Screen.print(1, "No Wi-Fi\r\n ");
        return false;
    }
}

bool is_button_A_clicked()
{
    static bool pressed = false;
    return is_button_clicked(USER_BUTTON_A, pressed);
}

bool is_button_B_clicked()
{
    static bool pressed = false;
    return is_button_clicked(USER_BUTTON_B, pressed);
}

bool is_button_clicked(unsigned char pin, bool &lastPressed)
{
    pinMode(pin, INPUT);
    auto pressed = digitalRead(pin) == LOW;
    auto clicked = false;
    if (pressed != lastPressed)
    {
        lastPressed = pressed;
        clicked = lastPressed;
    }
    return clicked;
}
