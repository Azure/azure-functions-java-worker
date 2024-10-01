package com.functions;

import java.util.*;

import com.microsoft.azure.functions.annotation.*;
import com.microsoft.azure.functions.*;


import java.util.Optional;

public class KafkaTriggerFunction {

    @FunctionName("HttpTriggerAndKafkaOutput")
    public HttpResponseMessage HttpTriggerAndKafkaOutput(
        @HttpTrigger(name = "req", methods = {HttpMethod.GET}, authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
        @KafkaOutput(
            name = "httpTriggerAndKafkaOutput",
            topic = "ci",
            brokerList = "%BrokerList%",
            username = "%ConfluentCloudUsername%",
            password = "%ConfluentCloudPassword%",
            authenticationMode = BrokerAuthenticationMode.PLAIN,
            sslCaLocation = "confluent_cloud_cacert.pem",
            protocol = BrokerProtocol.SASLSSL
        ) OutputBinding<String> output,
        final ExecutionContext context) {
        String message = request.getQueryParameters().get("message");
        message = request.getBody().orElse(message);
        context.getLogger().info("Java Http trigger received Message:" + message + " messages for Kafka Output");
        output.setValue(message);
        return request.createResponseBuilder(HttpStatus.OK).body(message).build();
    }

    @FunctionName("KafkaTriggerAndKafkaOutput")
    public void KafkaTriggerAndKafkaOutput(
        @KafkaTrigger(
            name = "kafkaTriggerAndKafkaOutput",
            topic = "ci",
            brokerList = "%BrokerList%",
            consumerGroup = "$Default",
            username = "%ConfluentCloudUsername%",
            password = "%ConfluentCloudPassword%",
            authenticationMode = BrokerAuthenticationMode.PLAIN,
            protocol = BrokerProtocol.SASLSSL,
            sslCaLocation = "confluent_cloud_cacert.pem",
            dataType = "string"
        ) String message,
        @QueueOutput(name = "output", queueName = "test-kafka-output-cardinality-one-java", connection = "AzureWebJobsStorage") OutputBinding<String> output,
        final ExecutionContext context) {
        context.getLogger().info("Java Kafka Output function processed a message: " + message);
        output.setValue(message);
    }
}
