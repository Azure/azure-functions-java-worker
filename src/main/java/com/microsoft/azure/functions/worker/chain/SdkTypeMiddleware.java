package com.microsoft.azure.functions.worker.chain;

import com.microsoft.azure.functions.internal.spi.middleware.Middleware;
import com.microsoft.azure.functions.internal.spi.middleware.MiddlewareChain;
import com.microsoft.azure.functions.internal.spi.middleware.MiddlewareContext;
import com.microsoft.azure.functions.worker.binding.ExecutionContextDataSource;
import com.microsoft.azure.functions.worker.binding.BindingDataStore;
import com.microsoft.azure.functions.worker.broker.MethodBindInfo;
import com.microsoft.azure.functions.worker.broker.ParamBindInfo;
import com.microsoft.azure.functions.worker.sdktype.SdkType;
import com.microsoft.azure.functions.worker.sdktype.SdkTypeRegistry;
import com.microsoft.azure.functions.worker.WorkerLogManager;

import java.util.logging.Logger;

/**
 * SdkTypeMiddleware is fully generic:
 * 1) for each param recognized by SdkTypeRegistry
 * 2) instantiate the SdkType
 * 3) parse the metadata
 * 4) call sdkType.hydrate() to get the final client object
 * 5) store in data store so param resolution sees it
 */
public class SdkTypeMiddleware implements Middleware {
    private static final Logger LOGGER = WorkerLogManager.getSystemLogger();
    private final ClassLoader classLoader;

    public SdkTypeMiddleware(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    @Override
    public void invoke(MiddlewareContext context, MiddlewareChain chain) throws Exception {

        // save the current loader
        ClassLoader prevCL = Thread.currentThread().getContextClassLoader();

        try {
            // set class loader for the reflection calls
            Thread.currentThread().setContextClassLoader(this.classLoader);

            ExecutionContextDataSource execCtx = (ExecutionContextDataSource) context;
            MethodBindInfo methodBindInfo = execCtx.getMethodBindInfo();
            //BindingDataStore dataStore = execCtx.getDataStore();

            for (ParamBindInfo param : methodBindInfo.getParams()) {
                String paramTypeFqcn = param.getType().getTypeName();

                if (SdkTypeRegistry.isRecognizedType(paramTypeFqcn)) {
                    SdkType sdkTypeInstance = SdkTypeRegistry.createSdkType(paramTypeFqcn);

                    // parse all needed metadata from the invocation context
                    sdkTypeInstance.parseMetadata(execCtx);

                    // build the final client object
                    Object sdkClient = sdkTypeInstance.hydrate();

                    // store in data store
                    execCtx.updateParameterValue(param.getName(), sdkClient);

                    LOGGER.info("SdkTypeMiddleware: Successfully created instance for param "
                            + param.getName() + " of type " + paramTypeFqcn);
                }
            }

            chain.doNext(context);
        } finally {
            Thread.currentThread().setContextClassLoader(prevCL);
        }
    }
}