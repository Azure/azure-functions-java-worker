package com.microsoft.azure.functions.worker.broker;

import java.lang.reflect.*;
import java.net.MalformedURLException;
import java.util.*;

import com.microsoft.azure.functions.worker.binding.*;
import com.microsoft.azure.functions.worker.description.*;
import com.microsoft.azure.functions.worker.reflect.*;
import com.microsoft.azure.functions.rpc.messages.*;

/**
 * Used to executor of arbitrary Java method in any JAR using reflection.
 * Thread-Safety: Multiple thread.
 */
public class EnhancedJavaMethodExecutorImpl extends AbstractJavaMethodExecutor {
    public EnhancedJavaMethodExecutorImpl(FunctionMethodDescriptor descriptor, Map<String, BindingInfo> bindingInfos, ClassLoaderProvider classLoaderProvider)
            throws MalformedURLException, ClassNotFoundException, NoSuchMethodException
    {
        super(descriptor, bindingInfos, classLoaderProvider);
    }

    public void execute(BindingDataStore dataStore) throws Exception {
        try {
            Thread.currentThread().setContextClassLoader(this.classLoader);
            Object retValue = this.overloadResolver.resolve(dataStore)
                    .orElseThrow(() -> new NoSuchMethodException("Cannot locate the method signature with the given input"))
                    .invoke(() -> createFunctionInstance());
            dataStore.setDataTargetValue(BindingDataStore.RETURN_NAME, retValue);
        } finally {
            Thread.currentThread().setContextClassLoader(ClassLoader.getSystemClassLoader());
        }
    }

    @Override
    protected Class<?> getContainingClass(String className) throws ClassNotFoundException {
        return Class.forName(className, false, this.classLoader);
    }

}
