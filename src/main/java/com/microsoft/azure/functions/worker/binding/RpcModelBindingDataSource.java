package com.microsoft.azure.functions.worker.binding;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.microsoft.azure.functions.rpc.messages.ModelBindingData;
import com.microsoft.azure.functions.worker.WorkerLogManager;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * A DataSource that parses "model_binding_data" from the host. The "content" field
 * is assumed to be JSON. We parse it into a Map<String,String>.
 * When someone calls "lookupName('ContainerName')", we return a nested DataSource
 * that acts like a string.
 */
public class RpcModelBindingDataSource extends DataSource<ModelBindingData> {
    private static final Logger LOGGER = WorkerLogManager.getSystemLogger();
    private static final Gson GSON = new Gson();

    // This holds the parsed key-value pairs from the model_binding_data.content JSON
    private final Map<String, String> contentMap;

    public RpcModelBindingDataSource(String name, ModelBindingData modelData) {
        super(name, modelData, MODEL_BINDING_DATA_OPERATIONS);

        // Parse the JSON in modelData.getContent() => Map<String,String>
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

    /**
     * The key method: if the user tries to get "lookupName('ContainerName')",
     * we see if "ContainerName" exists in contentMap. If so, we create a
     * nested DataSource (e.g. RpcStringDataSource) for it.
     */
    @Override
    protected Optional<DataSource<?>> lookupName(String subName) {
        if (contentMap.containsKey(subName)) {
            // Create a nested string data source so the code can do
            // getTriggerMetadataByName("ContainerName", String.class)
            // and eventually get that string value.
            String value = contentMap.get(subName);
            return Optional.of(new RpcStringDataSource(subName, value));
        }
        return Optional.empty();
    }

    // The operations can remain minimal, if you only do sub-value lookups
    // from "lookupName(...)". Or you might define operations for the entire Map.
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
    }
}
