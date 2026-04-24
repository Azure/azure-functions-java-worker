package com.microsoft.azure.functions.worker.binding;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.protobuf.InvalidProtocolBufferException;
import com.microsoft.azure.functions.rpc.messages.ModelBindingData;
import com.microsoft.azure.functions.worker.binding.kafka.KafkaRecord;
import com.microsoft.azure.functions.worker.binding.kafka.KafkaRecordProtoDeserializer;
import com.microsoft.azure.functions.worker.WorkerLogManager;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * A DataSource that parses "model_binding_data" from the host.
 *
 * <p>Dispatches based on content_type:
 * <ul>
 *   <li>"application/x-protobuf" with source "AzureKafkaRecord" — Protobuf deserialization to KafkaRecord</li>
 *   <li>all other — JSON parsing into Map&lt;String,String&gt; (legacy behavior)</li>
 * </ul>
 */
public class RpcModelBindingDataSource extends DataSource<ModelBindingData> {
    private static final Logger LOGGER = WorkerLogManager.getSystemLogger();
    private static final Gson GSON = new Gson();

    // This holds the parsed key-value pairs from the model_binding_data.content JSON (null for Protobuf path)
    private final Map<String, String> contentMap;

    // Precomputed KafkaRecord for Protobuf path (null for JSON path)
    private final KafkaRecord kafkaRecord;

    public RpcModelBindingDataSource(String name, ModelBindingData modelData) {
        super(name, modelData, MODEL_BINDING_DATA_OPERATIONS);

        String contentType = modelData.getContentType();
        String source = modelData.getSource();

        if (KafkaRecordProtoDeserializer.EXPECTED_CONTENT_TYPE.equals(contentType)
                && KafkaRecordProtoDeserializer.EXPECTED_SOURCE.equals(source)) {
            // Protobuf path: deserialize KafkaRecord
            this.contentMap = null;
            try {
                this.kafkaRecord = KafkaRecordProtoDeserializer.deserialize(
                        modelData.getContent().toByteArray());
            } catch (InvalidProtocolBufferException ex) {
                LOGGER.warning("Failed to deserialize KafkaRecord Protobuf: "
                        + ExceptionUtils.getRootCauseMessage(ex));
                throw new RuntimeException(ex);
            }
        } else {
            // JSON path: legacy behavior
            this.kafkaRecord = null;
            String jsonString = modelData.getContent().toStringUtf8();
            if (jsonString == null || jsonString.isEmpty()) {
                throw new IllegalArgumentException(
                        "model_binding_data.content is empty or missing for name: " + name
                );
            }

            Map<String,String> parsed = null;
            try {
                Type mapType = new TypeToken<Map<String, String>>(){}.getType();
                parsed = GSON.fromJson(jsonString, mapType);
            } catch (Exception ex) {
                LOGGER.warning("Failed to parse model_binding_data JSON: " + ExceptionUtils.getRootCauseMessage(ex));
                throw new RuntimeException(ex);
            }

            if (parsed == null) {
                throw new IllegalArgumentException(
                        "model_binding_data.content was not valid JSON for name: " + name
                );
            }
            this.contentMap = parsed;
        }
    }

    /**
     * The key method: if the user tries to get "lookupName('ContainerName')",
     * we see if "ContainerName" exists in contentMap. If so, we create a
     * nested DataSource (e.g. RpcStringDataSource) for it.
     */
    @Override
    protected Optional<DataSource<?>> lookupName(String subName) {
        if (contentMap != null && contentMap.containsKey(subName)) {
            String value = contentMap.get(subName);
            return Optional.of(new RpcStringDataSource(subName, value));
        }
        return Optional.empty();
    }

    // Package-private for testing
    KafkaRecord getKafkaRecord() {
        return kafkaRecord;
    }

    private static final DataOperations<ModelBindingData, Object> MODEL_BINDING_DATA_OPERATIONS
            = new DataOperations<>();

    static {
        // If someone tries to do computeByType(Map.class), return the entire contentMap.
        MODEL_BINDING_DATA_OPERATIONS.addGenericOperation(Map.class, (modelBindingData, targetType) -> {
            String json = modelBindingData.getContent().toStringUtf8();
            Type mapType = new TypeToken<Map<String, String>>(){}.getType();
            return GSON.fromJson(json, mapType);
        });

        // Or if they want it as a raw string, we can do that
        MODEL_BINDING_DATA_OPERATIONS.addOperation(String.class, modelBindingData -> modelBindingData.getContent());

        // KafkaRecord binding: deserialize Protobuf to KafkaRecord
        MODEL_BINDING_DATA_OPERATIONS.addOperation(KafkaRecord.class, modelBindingData -> {
            try {
                return KafkaRecordProtoDeserializer.deserialize(modelBindingData.getContent().toByteArray());
            } catch (InvalidProtocolBufferException ex) {
                throw new RuntimeException("Failed to deserialize KafkaRecord Protobuf", ex);
            }
        });
    }
}
