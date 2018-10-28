package com.microsoft.azure.functions.worker.binding;

import java.lang.reflect.*;
import java.net.*;
import java.util.*;
import java.util.logging.*;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.core.JsonParser;

import org.apache.commons.lang3.reflect.*;

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.worker.*;
import com.microsoft.azure.functions.rpc.messages.*;

final class RpcJsonDataSource extends DataSource<String> {
    RpcJsonDataSource(String name, String value) { super(name, value, JSON_DATA_OPERATIONS); }

    private static final ObjectMapper RELAXED_JSON_MAPPER = new ObjectMapper();
    private static final DataOperations<String, Object> JSON_DATA_OPERATIONS = new DataOperations<>();

    static {
        RELAXED_JSON_MAPPER.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        RELAXED_JSON_MAPPER.setVisibility(PropertyAccessor.CREATOR, JsonAutoDetect.Visibility.ANY);
        RELAXED_JSON_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        RELAXED_JSON_MAPPER.enable(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES);

        JSON_DATA_OPERATIONS.addOperation(String.class, s -> s);
        JSON_DATA_OPERATIONS.addOperation(String[].class, s -> RELAXED_JSON_MAPPER.readValue(s, String[].class));        
    }
}
