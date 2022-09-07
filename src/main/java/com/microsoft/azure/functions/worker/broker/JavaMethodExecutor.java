package com.microsoft.azure.functions.worker.broker;


import java.util.*;
import com.microsoft.azure.functions.worker.binding.*;


/**
 * Used to executor of arbitrary Java method in any JAR using reflection.
 * Thread-Safety: Multiple thread.
 */
public interface JavaMethodExecutor {
    Map<String, BindingDefinition> getBindingDefinitions();

    ParameterResolver getOverloadResolver();

    MethodBindInfo getMethodBindInfo();

    void execute(ExecutionContextDataSource executionContextDataSource) throws Exception;
}
