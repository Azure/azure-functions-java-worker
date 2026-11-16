package com.microsoft.azure.functions.worker.chain;

import com.microsoft.azure.functions.cache.CacheKey;
import com.microsoft.azure.functions.internal.spi.middleware.Middleware;
import com.microsoft.azure.functions.internal.spi.middleware.MiddlewareChain;
import com.microsoft.azure.functions.internal.spi.middleware.MiddlewareContext;
import com.microsoft.azure.functions.worker.binding.BindingData;
import com.microsoft.azure.functions.worker.binding.BindingDataStore;
import com.microsoft.azure.functions.worker.binding.ExecutionContextDataSource;
import com.microsoft.azure.functions.worker.broker.ParamBindInfo;
import com.microsoft.azure.functions.worker.cache.WorkerObjectCache;
import com.microsoft.azure.functions.worker.WorkerLogManager;
import com.microsoft.azure.functions.sdktype.CachableSdkType;
import com.microsoft.azure.functions.sdktype.SdkType;
import com.microsoft.azure.functions.sdktype.SdkTypeDescriptor;
import com.microsoft.azure.functions.sdktype.SdkTypeMetaData;
import com.microsoft.azure.functions.sdktype.SdkTypeRegistry;

import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Generic middleware that:
 *  1) Loops over the SdkTypeDescriptors discovered for the function
 *  2) Binds each descriptor to this invocation's values, which yields immutable metadata
 *  3) Uses WorkerObjectCache to store/retrieve final object
 *  4) Updates the ExecutionContextDataSource with the instance
 */
public class SdkTypeMiddleware implements Middleware {
    private static final Logger LOGGER = WorkerLogManager.getSystemLogger();
    private final ClassLoader classLoader;
    // Descriptors are immutable and shared by every invocation of the function.
    private final List<SdkTypeDescriptor> sdkTypeDescriptors;
    private final SdkTypeRegistry sdkTypeRegistry;


    public SdkTypeMiddleware(ClassLoader classLoader, List<SdkTypeDescriptor> sdkTypeDescriptors, SdkTypeRegistry sdkTypeRegistry) {
        this.classLoader = classLoader;
        this.sdkTypeDescriptors = Collections.unmodifiableList(new ArrayList<>(sdkTypeDescriptors));
        this.sdkTypeRegistry = sdkTypeRegistry;
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

            for (SdkTypeDescriptor descriptor : this.sdkTypeDescriptors) {
                Parameter param = descriptor.getParam();
                ParamBindInfo paramBindInfo = new ParamBindInfo(param);

                // The cache key and the instance below are both derived from this
                // invocation's immutable metadata, so concurrent invocations cannot mix values.
                SdkTypeMetaData metaData = bindMetaData(execCtx, descriptor, paramBindInfo.getName());
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
                    instance = sdkType.buildInstance();
                }

                // update in data store
                execCtx.updateParameterValue(paramBindInfo.getName(), instance);

                LOGGER.info("SdkTypeMiddleware: Successfully created instance for param "
                        + param.getName() + " of type " + param.getType());
            }
        } finally {
            Thread.currentThread().setContextClassLoader(prevCL);
        }

        chain.doNext(context);
    }

    /**
     * Collect the values required by the {@link SdkTypeDescriptor} for this invocation and
     * bind them into a new immutable {@link SdkTypeMetaData}. Throws a single informative
     * exception if any required key is missing.
     */
    private SdkTypeMetaData bindMetaData(ExecutionContextDataSource execCtx, SdkTypeDescriptor descriptor, String inputSourceName) {

        BindingDataStore dataStore = execCtx.getDataStore();
        Map<String, String> rawValues = new HashMap<>();
        List<String> missing = new ArrayList<>();

        for (String key : descriptor.getRequiredFields()) {
            Optional<BindingData> opt = dataStore
                    .getDataByNameFromInputSource(key, String.class, inputSourceName);

            Object value = opt.isPresent() ? opt.get().getValue() : null;
            if (value != null) {
                rawValues.put(key, value.toString());
            } else {
                missing.add(key);
            }
        }

        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(String.format(
                    "Parameter '%s' in function '%s' (invocation %s) is missing %d required "
                  + "key(s): %s",
                    inputSourceName,
                    execCtx.getFunctionName(),
                    execCtx.getInvocationId(),
                    missing.size(),
                    String.join(", ", missing)));
        }

        return descriptor.bind(rawValues);
    }
}