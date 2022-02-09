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

    void execute(BindingDataStore dataStore) throws Exception;
}
