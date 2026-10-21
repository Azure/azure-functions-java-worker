package com.microsoft.azure.functions.worker.binding;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.microsoft.azure.functions.rpc.messages.ModelBindingData;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Optional;

/**
 * Preserves the host envelope. Legacy Blob metadata is decoded only when a
 * required string field is requested, never when binary SDK data arrives.
 */
public class RpcModelBindingDataSource extends DataSource<ModelBindingData> {
    private static final Gson GSON = new Gson();
    private Map<String, String> contentMap;

    public RpcModelBindingDataSource(String name, ModelBindingData modelData) {
        super(name, modelData, MODEL_BINDING_DATA_OPERATIONS);

    }

    @Override
    protected Optional<DataSource<?>> lookupName(String subName) {
        Optional<DataSource<?>> source = super.lookupName(subName);
        if (source.isPresent()) {
            return source;
        }
        if (!isJsonMetadata(getValue())) {
            return Optional.empty();
        }
        if (contentMap == null) {
            contentMap = parseContent(getValue());
        }
        if (contentMap.containsKey(subName)) {
            String value = contentMap.get(subName);
            return Optional.of(new RpcStringDataSource(subName, value));
        }
        return Optional.empty();
    }

    private static Map<String, String> parseContent(ModelBindingData data) {
        if (!isJsonMetadata(data)) {
            throw new IllegalArgumentException("Model binding data is not JSON: " + data.getContentType());
        }
        Type mapType = new TypeToken<Map<String, String>>(){}.getType();
        Map<String, String> parsed = GSON.fromJson(data.getContent().toStringUtf8(), mapType);
        if (parsed == null) {
            throw new IllegalArgumentException("model_binding_data.content is empty or not a JSON object");
        }
        return parsed;
    }

    private static boolean isJsonMetadata(ModelBindingData data) {
        // Older Blob hosts did not always populate content_type.
        return data.getContentType().isEmpty() || "application/json".equalsIgnoreCase(data.getContentType());
    }

    private static final DataOperations<ModelBindingData, Object> MODEL_BINDING_DATA_OPERATIONS
            = new DataOperations<>();

    static {
        MODEL_BINDING_DATA_OPERATIONS.addGenericOperation(Map.class, (data, targetType) -> parseContent(data));
        MODEL_BINDING_DATA_OPERATIONS.addOperation(String.class, data -> data.getContent().toStringUtf8());
    }
}
