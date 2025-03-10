package com.microsoft.azure.functions.worker.chain;

import com.microsoft.azure.functions.cache.CacheKey;
import com.microsoft.azure.functions.internal.spi.middleware.Middleware;
import com.microsoft.azure.functions.internal.spi.middleware.MiddlewareChain;
import com.microsoft.azure.functions.internal.spi.middleware.MiddlewareContext;
import com.microsoft.azure.functions.worker.binding.ExecutionContextDataSource;
import com.microsoft.azure.functions.worker.broker.ParamBindInfo;
import com.microsoft.azure.functions.worker.cache.WorkerObjectCache;
import com.micsrosoft.azure.functions.sdktype.SdkType;
import com.microsoft.azure.functions.worker.WorkerLogManager;

import java.lang.reflect.Parameter;
import java.util.List;
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
    private final List<SdkType<?>> sdkTypes;


    public SdkTypeMiddleware(ClassLoader classLoader, List<SdkType<?>> sdkTypes) {
        this.classLoader = classLoader;
        this.sdkTypes = sdkTypes;
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

            for (SdkType<?> sdkType : this.sdkTypes) {
                sdkType.parseMetadata(execCtx);
                Parameter param = sdkType.getParam();
                ParamBindInfo paramBindInfo = new ParamBindInfo(param);

                CacheKey key = sdkType.buildCacheKey();
                Object instance = cache.computeIfAbsent(
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
}