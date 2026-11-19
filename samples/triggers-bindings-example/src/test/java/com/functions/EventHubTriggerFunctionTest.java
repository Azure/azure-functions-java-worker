package com.functions;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class EventHubTriggerFunctionTest {
    /**
     * Unit test for EventHubTriggerFunction class.
     */
    // Define the shared variables globally.
    ExecutionContext context;
    EventHubTriggerFunction eventHubTriggerFunction;
    OutputBinding<String> output;
    OutputBinding<byte[]> outputAsByteArray;

    // Before the tests are run, execute the below configurations.
    @BeforeEach
    void setup() {
        // Mock the Execution Context and its logger since we will be using this in the Azure Functions.
        this.context = mock(ExecutionContext.class);
        doReturn(Logger.getGlobal()).when(context).getLogger();

        // Define the properties of the OutputBinding object.
        this.output = new OutputBinding<String>() {
            String value;

            @Override
            public String getValue() {
                return value;
            }

            @Override
            public void setValue(String value) {
                this.value = value;
            }
        };

        this.outputAsByteArray = new OutputBinding<byte[]>() {
            byte[] value;

            @Override
            public byte[] getValue() {
                return value;
            }

            @Override
            public void setValue(byte[] value) {
                this.value = value;
            }
        };

        // Instantiate the EventHubTriggerFunction class so that we can call its methods.
        this.eventHubTriggerFunction = new EventHubTriggerFunction();
    }

    @Test
    public void testEventHubTriggerAndOutputJSONJava() throws Exception {
        List<String> messages = new ArrayList<>();
        for(int i = 0; i < 3; ++i) {
            messages.add("Message" + i);
        }

        eventHubTriggerFunction.EventHubTriggerAndOutputJSON(messages, output, context);

        assertEquals(messages.get(0), output.getValue());
    }

    @Test
    public void testEventHubTriggerAndOutputStringJava() throws Exception {
        EventHubTriggerFunction.SystemProperty[] systemPropertiesArray;
        String[] messages = new String[4];
        List<EventHubTriggerFunction.SystemProperty> systemPropertyList = new ArrayList<>();

        for(int i = 0; i < 3; ++i) {
            messages[i] = "Message" + i;
            EventHubTriggerFunction.SystemProperty systemProperty = new EventHubTriggerFunction.SystemProperty("Sequence Number: " + i,
                "Offset: " + (i * 2),
                "Partition Key: " + (i * 3),
                "EnqueuedTimeUtc: " + Instant.now());

            systemPropertyList.add(systemProperty);
        }

        systemPropertiesArray = systemPropertyList.toArray(new EventHubTriggerFunction.SystemProperty[0]);

        eventHubTriggerFunction.EventHubTriggerAndOutputString(messages, systemPropertiesArray, output, context);

        assertEquals(messages[0], output.getValue());
    }

    @Test
    public void testEventHubTriggerCardinalityOneJava() throws Exception {
        String message = "Hello World!";

        eventHubTriggerFunction.EventHubTriggerCardinalityOne(message, output, context);

        assertEquals(message, output.getValue());
    }

    @Test
    public void testEventHubTriggerAndOutputBinaryCardinalityManyListBinaryJava() throws Exception {
        String message = "Hello World!";
        byte[] messageAsByteArray = message.getBytes(StandardCharsets.UTF_8);

        List<byte[]> messageByteArrayList = new ArrayList<>();
        messageByteArrayList.add(messageAsByteArray);

        eventHubTriggerFunction.EventHubTriggerAndOutputBinaryCardinalityManyListBinary(messageByteArrayList, outputAsByteArray, context);

        assertEquals(messageByteArrayList.get(0), outputAsByteArray.getValue());
    }

    @Test
    public void testEventHubTriggerAndOutputBinaryCardinalityOneJava() throws Exception {
        String message = "Hello World!";
        byte[] messageAsByteArray = message.getBytes(StandardCharsets.UTF_8);

        eventHubTriggerFunction.EventHubTriggerAndOutputBinaryCardinalityOne(messageAsByteArray, outputAsByteArray, context);

        assertEquals(messageAsByteArray, outputAsByteArray.getValue());
    }

    @Test
    public void testEventHubTriggerAndOutputBinaryCardinalityManyArrayBinaryJava() throws Exception {
        byte[][] twoDimensionalByteArray = new byte[2][2];

        eventHubTriggerFunction.EventHubTriggerAndOutputBinaryCardinalityManyArrayBinary(twoDimensionalByteArray, outputAsByteArray, context);

        assertEquals(twoDimensionalByteArray[0], outputAsByteArray.getValue());
    }
}
