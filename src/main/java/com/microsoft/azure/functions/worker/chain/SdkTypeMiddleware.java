package com.microsoft.azure.functions.worker.chain;

import com.microsoft.azure.functions.cache.CacheKey;
import com.microsoft.azure.functions.internal.spi.middleware.Middleware;
import com.microsoft.azure.functions.internal.spi.middleware.MiddlewareChain;
import com.microsoft.azure.functions.internal.spi.middleware.MiddlewareContext;
import com.microsoft.azure.functions.rpc.messages.TypedData;
import com.microsoft.azure.functions.worker.binding.BindingData;
import com.microsoft.azure.functions.worker.binding.BindingDataStore;
import com.microsoft.azure.functions.worker.binding.ExecutionContextDataSource;
import com.microsoft.azure.functions.worker.binding.WorkerSdkInvocationContext;
import com.microsoft.azure.functions.worker.broker.ParamBindInfo;
import com.microsoft.azure.functions.worker.cache.WorkerObjectCache;
import com.microsoft.azure.functions.worker.WorkerLogManager;
import com.microsoft.azure.functions.sdktype.CachableSdkType;
import com.microsoft.azure.functions.sdktype.SdkType;
import com.microsoft.azure.functions.sdktype.SdkTypeRegistry;
import com.microsoft.azure.functions.sdktype.SdkTypeMetaData;

import java.lang.reflect.Parameter;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Generic middleware that:
 *  1) Loops over discovered SdkTypes
 *  2) parseMetadata
 *  3) Uses WorkerObjectCache to store/retrieve final object
 *  4) Updates the ExecutionContextDataSource with the instance
 */
public class SdkTypeMiddleware implements Middleware {
    private static final Logger LOGGER = WorkerLogManager.getSystemLogger();
    private final ClassLoader classLoader;
    private final List<SdkTypeMetaData> sdkTypesMetaData;
    private final SdkTypeRegistry sdkTypeRegistry;
    private final URI hostEndpoint;

    public SdkTypeMiddleware(ClassLoader classLoader, List<SdkTypeMetaData> sdkTypesMetaData,
                             SdkTypeRegistry sdkTypeRegistry, URI hostEndpoint) {
        this.classLoader = classLoader;
        this.sdkTypesMetaData = sdkTypesMetaData;
        this.sdkTypeRegistry = sdkTypeRegistry;
        this.hostEndpoint = hostEndpoint;
    }

    @Override
    public void invoke(MiddlewareContext context, MiddlewareChain chain) throws Exception {

        // save the current loader
        ClassLoader prevCL = Thread.currentThread().getContextClassLoader();
        // set class loader for the reflection calls
        Thread.currentThread().setContextClassLoader(this.classLoader);

        try {
            ExecutionContextDataSource execCtx = (ExecutionContextDataSource) context;
            WorkerObjectCache<CacheKey> cache = execCtx.getCache();

            for (SdkTypeMetaData template : this.sdkTypesMetaData) {
                Parameter param = template.getParam();
                ParamBindInfo paramBindInfo = new ParamBindInfo(param);
                String name = paramBindInfo.getName();
                boolean named = name != null && !name.isEmpty();
                TypedData input = named ? execCtx.getInputData(name) : null;
                requireModelBindingData(execCtx, name, input);

                // The template is shared by every invocation of this function, so fill a fresh copy.
                SdkTypeMetaData metaData = this.sdkTypeRegistry.createMetaData(template.getFqcn(), param);

                fillMetaData(execCtx, metaData, name);
                SdkType<?> sdkType = this.sdkTypeRegistry.createSdkType(metaData);

                Object instance = null;
                if (sdkType instanceof CachableSdkType) {
                    CacheKey key = ((CachableSdkType<?>) sdkType).buildCacheKey();
                    instance = cache.computeIfAbsent(
                            this.getClass(),
                            key,
                            () -> {
                                try {
                                    return sdkType.buildInstance();
                                } catch (Exception ex) {
                                    throw new RuntimeException(ex);
                                }
                            }
                    );
                } else {
                    instance = sdkType.buildInstance(new WorkerSdkInvocationContext(input, this.hostEndpoint));
                }

                // update in data store
                if (named) {
                    execCtx.updateParameterValue(name, instance);
                } else {
                    execCtx.updateParameterValueByType(paramBindInfo.getType(), instance);
                }

                LOGGER.info("SdkTypeMiddleware: Successfully created instance for param "
                        + param.getName() + " of type " + param.getType());
            }
        } finally {
            Thread.currentThread().setContextClassLoader(prevCL);
        }

        chain.doNext(context);
    }

    /**
     * The host sends a plain body instead of model binding data when it does not defer binding to the
     * worker, for example when other bindings of the function use binding expressions.
     */
    private static void requireModelBindingData(ExecutionContextDataSource execCtx, String name, TypedData input) {
        if (input != null && !input.hasModelBindingData() && !input.hasCollectionModelBindingData()) {
            throw new IllegalStateException(String.format(
                    "Parameter '%s' in function '%s' (invocation %s) is an SDK type, but the host sent '%s' data "
                  + "instead of model binding data. The host does not defer binding to the worker for this "
                  + "function. Check for binding expressions such as {name} in its bindings.",
                    name, execCtx.getFunctionName(), execCtx.getInvocationId(), input.getDataCase()));
        }
    }

    /**
     * Populate the {@link SdkTypeMetaData} with required field values and ensure
     * every required key is present. Throws a single informative exception if any
     * are missing. 
     * 
     * After successfully filling in all required data, a call to 
     * SdkTypeMetaData::parseAndVerify is made using the input metaData.
     */
    private void fillMetaData(ExecutionContextDataSource execCtx, SdkTypeMetaData metaData, String inputSourceName) {

        BindingDataStore dataStore = execCtx.getDataStore();
        Set<String> requiredKeys = metaData.getRequiredFields();
        List<String> missing = new ArrayList<>();

        for (String key : requiredKeys) {
            Optional<BindingData> opt = dataStore
                    .getDataByNameFromInputSource(key, String.class, inputSourceName);

            if (opt.isPresent()) {
                metaData.setFieldValue(key, opt.get().getValue());
            } else {
                missing.add(key);
            }
        }

        if (missing.isEmpty()) {
            metaData.parseAndVerify();
        } else {
            throw new IllegalArgumentException(String.format(
                    "Parameter '%s' in function '%s' (invocation %s) is missing %d required "
                  + "key(s): %s",
                    inputSourceName,
                    execCtx.getFunctionName(),
                    execCtx.getInvocationId(),
                    missing.size(),
                    String.join(", ", missing)));
        }
    }
}