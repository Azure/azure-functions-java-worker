#include "precomp.h"

static IOTHUB_CLIENT_LL_HANDLE iotHubClientHandle;
static bool hasNewMessage = false;
static char messageString[MESSAGE_MAX_LENGTH];
static IOTHUBMESSAGE_DISPOSITION_RESULT message_callback(IOTHUB_MESSAGE_HANDLE, void *);
static void send_confirmation_callback(IOTHUB_CLIENT_CONFIRMATION_RESULT, void *);
static bool isSending = false, messageSent = true;

bool iothub_initialize()
{
    EEPROMInterface eeprom;
    uint8_t connString[AZ_IOT_HUB_MAX_LEN + 1] = { '\0' };
    int ret = eeprom.read(connString, AZ_IOT_HUB_MAX_LEN, 0x00, AZ_IOT_HUB_ZONE_IDX);
    if (ret < 0)
    { 
        LogError("Unable to get the azure iot connection string from EEPROM. Please set the value in configuration mode.");
        return false;
    }
    else if (ret == 0)
    {
        LogError("The connection string is empty. Please set the value in configuration mode.");
        return false;
    }
    if (platform_init() != 0)
    {
        LogError("Failed to initialize the platform.");
        return false;
    }
    if ((iotHubClientHandle = IoTHubClient_LL_CreateFromConnectionString((char*)connString, MQTT_Protocol)) == nullptr)
    {
        LogError("iotHubClientHandle is NULL!");
        return false;
    }
    if (IoTHubClient_LL_SetOption(iotHubClientHandle, "TrustedCerts", certificates) != IOTHUB_CLIENT_OK)
    {
        LogError("Failed to set option \"TrustedCerts\"");
        return false;
    }
    if (IoTHubClient_LL_SetMessageCallback(iotHubClientHandle, message_callback, nullptr) != IOTHUB_CLIENT_OK)
    {
        LogError("IoTHubClient_LL_SetMessageCallback failed");
        return false;
    }
    return true;
}

void iothub_synchronize(void)
{
    IoTHubClient_LL_DoWork(iotHubClientHandle);
}

const char *iothub_recv()
{
    if (hasNewMessage)
    {
        hasNewMessage = false;
        return messageString;
    }
    return nullptr;
}

bool iothub_send(const char *text)
{
    auto message = IoTHubMessage_CreateFromByteArray(reinterpret_cast<const unsigned char *>(text), strlen(text));
    if (message == nullptr)
    {
        LogError("Failed to create a new IoTHubMessage");
        return false;
    }
    if (IoTHubClient_LL_SendEventAsync(iotHubClientHandle, message, send_confirmation_callback, nullptr) != IOTHUB_CLIENT_OK)
    {
        LogError("Failed to hand over the message to IoTHubClient");
        return false;
    }
    isSending = true;
    messageSent = false;
    LogInfo("Sending D2C message: %s", text);
    IoTHubMessage_Destroy(message);
}

bool iothub_is_sending()
{
    return isSending;
}

bool iothub_last_sent()
{
    return messageSent;
}

IOTHUBMESSAGE_DISPOSITION_RESULT message_callback(IOTHUB_MESSAGE_HANDLE message, void *)
{
    const unsigned char *buffer;
    size_t size;

    if (IoTHubMessage_GetByteArray(message, &buffer, &size) != IOTHUB_MESSAGE_OK)
    {
        LogError("IoTHubMessage_GetByteArray failed");
        return IOTHUBMESSAGE_REJECTED;
    }
    else
    {
        if (size >= MESSAGE_MAX_LENGTH)
        {
            LogError("C2D message too big");
            return IOTHUBMESSAGE_REJECTED;
        }
        memcpy(messageString, buffer, size);
        messageString[size] = '\0';
        hasNewMessage = true;
        LogInfo("New C2D message arrived: %s", messageString);
        return IOTHUBMESSAGE_ACCEPTED;
    }
}

void send_confirmation_callback(IOTHUB_CLIENT_CONFIRMATION_RESULT result, void *)
{
    if (IOTHUB_CLIENT_CONFIRMATION_OK == result)
    {
        messageSent = true;
        LogInfo("D2C message sent");
    }
    else
    {
        LogError("Failed to send D2C message");
    }
    isSending = false;
}
