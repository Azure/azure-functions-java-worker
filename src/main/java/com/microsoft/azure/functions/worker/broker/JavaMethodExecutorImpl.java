package com.microsoft.azure.functions.worker.broker;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;
import java.util.logging.Logger;

import com.microsoft.azure.functions.worker.WorkerLogManager;
import com.microsoft.azure.functions.worker.binding.*;
import com.microsoft.azure.functions.worker.description.*;
import com.microsoft.azure.functions.worker.reflect.*;
import com.microsoft.azure.functions.rpc.messages.*;

/**
 * Used to executor of arbitrary Java method in any JAR using reflection.
 * Thread-Safety: Multiple thread.
 */
public class JavaMethodExecutorImpl extends AbstractJavaMethodExecutor {
    public JavaMethodExecutorImpl(FunctionMethodDescriptor descriptor, Map<String, BindingInfo> bindingInfos, ClassLoaderProvider classLoaderProvider)
            throws MalformedURLException, ClassNotFoundException, NoSuchMethodException
    {
        super(descriptor, bindingInfos, classLoaderProvider);
    }

    public void execute(BindingDataStore dataStore) throws Exception {
        Object retValue = this.overloadResolver.resolve(dataStore)
                .orElseThrow(() -> new NoSuchMethodException("Cannot locate the method signature with the given input"))
                .invoke(() -> createFunctionInstance());
        dataStore.setDataTargetValue(BindingDataStore.RETURN_NAME, retValue);
    }

    @Override
    protected Class<?> getContainingClass(String className) throws ClassNotFoundException {
        return Class.forName(className, true, classLoader);
    }


}
