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
import com.microsoft.azure.functions.sdktype.SdkTypeRegistry;
import com.microsoft.azure.functions.sdktype.SdkTypeMetaData;

import java.lang.reflect.Parameter;
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


    public SdkTypeMiddleware(ClassLoader classLoader, List<SdkTypeMetaData> sdkTypesMetaData, SdkTypeRegistry sdkTypeRegistry) {
        this.classLoader = classLoader;
        this.sdkTypesMetaData = sdkTypesMetaData;
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

            for (SdkTypeMetaData metaData : this.sdkTypesMetaData) {
                Parameter param = metaData.getParam();
                ParamBindInfo paramBindInfo = new ParamBindInfo(param);

                fillMetaData(execCtx, metaData, paramBindInfo.getName());
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