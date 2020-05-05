package com.microsoft.azure.functions.worker.broker;

import java.lang.reflect.*;
import java.net.*;
import java.util.*;

import com.microsoft.azure.functions.annotation.*;
import com.microsoft.azure.functions.worker.binding.*;
import com.microsoft.azure.functions.worker.description.*;
import com.microsoft.azure.functions.worker.reflect.*;
import com.microsoft.azure.functions.rpc.messages.*;

/**
 * Used to executor of arbitrary Java method in any JAR using reflection.
 * Thread-Safety: Multiple thread.
 */
public interface JavaMethodExecutor {
    Map<String, BindingDefinition> getBindingDefinitions();

    ParameterResolver getOverloadResolver();

    void execute(BindingDataStore dataStore) throws Exception;
}
