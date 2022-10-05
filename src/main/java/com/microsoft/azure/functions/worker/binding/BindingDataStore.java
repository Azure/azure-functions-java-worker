package com.microsoft.azure.functions.worker.binding;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.rpc.messages.ParameterBinding;
import com.microsoft.azure.functions.rpc.messages.TypedData;
import com.microsoft.azure.functions.worker.broker.CoreTypeResolver;

import org.apache.commons.lang3.exception.ExceptionUtils;

/**
 * A warehouse storing all binding related information including actual binding value as well as binding declaration info.
 * This warehouse also provides methods to validate/look-up/convert against Java types.
 * Thread-safety: Single thread.
 */
public final class BindingDataStore {
    public BindingDataStore() {
        this.targets = new HashMap<>();
        this.inputSources = new HashMap<>();
        this.otherSources = new HashMap<>();
        this.metadataSources = new HashMap<>();
        this.promotedTargets = null;
    }

    ///////////////////////// region Input Binding Data

    public void addParameterSources(List<ParameterBinding> parameters) {
        assert parameters != null;
        for (ParameterBinding parameter : parameters) {
        	DataSource<?> inputValue = rpcSourceFromParameter(parameter);
            this.inputSources.put(parameter.getName(), inputValue);
        }
    }

    public void addTriggerMetadataSource(Map<String, TypedData> metadata) {
        for (Map.Entry<String,TypedData> entry : metadata.entrySet())
        {
        	DataSource<?> inputValue = rpcSourceFromTypedData(entry.getKey(), entry.getValue());
            this.metadataSources.put(entry.getKey(), inputValue);
        }
    }

    public void addExecutionContextSource(ExecutionContextDataSource executionContextDataSource) {
        otherSources.put(ExecutionContext.class,executionContextDataSource);
    }

    public Optional<BindingData> getDataByName(String name, Type target) {
        DataSource<?> parameterDataSource = this.inputSources.get(name);
        if (parameterDataSource == null) {
            throw new RuntimeException("Cannot find matched parameter name of customer function, please check if customer function is defined correctly");
        }
    	return parameterDataSource.computeByName(name, target);
    }

    public Optional<BindingData> getTriggerMetatDataByName(String name, Type target) {
        DataSource<?> metadataDataSource = this.metadataSources.get(name);
        if (metadataDataSource == null) {
            throw new RuntimeException("Cannot find matched @BindingName of customer function, please check if customer function is defined correctly");
        }
    	return metadataDataSource.computeByName(name, target);
    }

    public Optional<BindingData> getDataByType(Type target) {
    	return this.otherSources.get(ExecutionContext.class).computeByType(target);
    }

    static DataSource<?> rpcSourceFromTypedData(String name, TypedData data) {
        switch (data.getDataCase()) {
            case INT:    return new RpcIntegerDataSource(name, data.getInt());
            case DOUBLE: return new RpcRealNumberDataSource(name, data.getDouble());
            case STRING: return new RpcStringDataSource(name, data.getString());
            case BYTES:  return new RpcByteArrayDataSource(name, data.getBytes());
            case JSON:   return new RpcJsonDataSource(name, data.getJson());
            case HTTP:   return new RpcHttpRequestDataSource(name, data.getHttp());
            case COLLECTION_STRING: return new RpcCollectionStringDataSource(name, data.getCollectionString());
            case COLLECTION_DOUBLE: return new RpcCollectionDoubleDataSource(name, data.getCollectionDouble());
            case COLLECTION_BYTES: return new RpcCollectionByteArrayDataSource(name, data.getCollectionBytes());
            case COLLECTION_SINT64: return new RpcCollectionLongDataSource(name, data.getCollectionSint64());
            case DATA_NOT_SET: return new RpcEmptyDataSource(name);
            default:     throw new UnsupportedOperationException("Input data type \"" + data.getDataCase() + "\" is not supported");
        }
    }

    private static DataSource<?> rpcSourceFromParameter(ParameterBinding parameter) {
        return rpcSourceFromTypedData(parameter.getName(), parameter.getData());
    }

    ///////////////////////// end region Input Binding Data

    ///////////////////////// region Output Binding Data

    public List<ParameterBinding> getOutputParameterBindings(boolean excludeReturn) throws Exception {
        List<ParameterBinding> bindings = new ArrayList<>();
        for (Map.Entry<String, DataTarget> entry : this.getTarget(this.promotedTargets).entrySet()) {
            if (!excludeReturn || !entry.getKey().equals(RETURN_NAME)) {
                entry.getValue().computeFromValue().ifPresent(data ->
                    bindings.add(ParameterBinding.newBuilder().setName(entry.getKey()).setData(data).build())
                );
            }
        }
        return bindings;
    }

    public Optional<TypedData> getDataTargetTypedValue(String name) throws Exception{
    	return Optional.ofNullable(this.getTarget(this.promotedTargets).get(name)).map(o -> {
			try {
				return o.computeFromValue().orElse(null);
			} catch (Exception ex) {
				ExceptionUtils.rethrow(ex);
				return null;
			}
		});
    }

    public Optional<BindingData> getOrAddDataTarget(UUID outputId, String name, Type target, boolean hasImplicitOutput) {
        DataTarget output = null;
        if (this.isDataTargetValid(name, target)) {
            output = this.getTarget(outputId).get(name);
            if (output == null && (this.isDefinitionOutput(name) || hasImplicitOutput)) {
                this.getTarget(outputId).put(name, output = rpcDataTargetFromType(target));
            }
        }
        return Optional.ofNullable(output).map(out -> new BindingData(out));
    }

    public void setDataTargetValue(String name, Object value) {
        Optional.ofNullable(this.getTarget(this.promotedTargets).get(name)).ifPresent(out -> out.setValue(value));
    }

    public void promoteDataTargets(UUID outputId) {
        this.promotedTargets = outputId;
    }

    private Map<String, DataTarget> getTarget(UUID outputId) {
        return this.targets.computeIfAbsent(outputId, m -> new HashMap<>());
    }

    private boolean isDataTargetValid(String name, Type target) {
        if (!name.equals(RETURN_NAME)) {
            if (!CoreTypeResolver.isValidOutputType(target)) { return false; }
            target = CoreTypeResolver.getParameterizedActualTypeArgumentsType(target);
        }
        return true;
    }

    private static DataTarget rpcDataTargetFromType(Type target) {
        if (CoreTypeResolver.isHttpResponse(target)) {
            return new RpcHttpDataTarget();
        }
        return new RpcUnspecifiedDataTarget();
    }

    ///////////////////////// end region Output Binding Data

    ///////////////////////// region Binding Definitions

    public void setBindingDefinitions(Map<String, BindingDefinition> definitions) {
        this.definitions = definitions;
    }

    private boolean isDefinitionOutput(String name) {
        return this.getDefinition(name).map(BindingDefinition::isOutput).orElse(false);
    }

    private Optional<BindingDefinition> getDefinition(String name) {
        return Optional.ofNullable(this.definitions.get(name));
    }

    ///////////////////////// endregion Binding Definitions

    private UUID promotedTargets;
    private final Map<UUID, Map<String, DataTarget>> targets;
    private final Map<String, DataSource<?>> inputSources;
    private final Map<Type, DataSource<?>> otherSources;
    private final Map<String, DataSource<?>> metadataSources;
    private Map<String, BindingDefinition> definitions;
    public static final String RETURN_NAME = "$return";
}
