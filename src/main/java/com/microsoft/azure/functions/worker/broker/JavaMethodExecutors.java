package com.microsoft.azure.functions.worker.broker;

import com.microsoft.azure.functions.worker.WorkerLogManager;
import org.apache.commons.lang3.SystemUtils;

public class JavaMethodExecutors {
    public static JavaMethodExecutor createJavaMethodExecutor(ClassLoader classLoader) {
        if(SystemUtils.IS_JAVA_1_8) {
            WorkerLogManager.getSystemLogger().info("Loading JavaMethodExecutorImpl");
            return JavaMethodExecutorImpl.getInstance();
        } else {
            WorkerLogManager.getSystemLogger().info("Loading EnhancedJavaMethodExecutorImpl");
            return new EnhancedJavaMethodExecutorImpl(classLoader);
        }
    }
}
