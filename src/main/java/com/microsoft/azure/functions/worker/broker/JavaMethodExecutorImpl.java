package com.microsoft.azure.functions.worker.broker;

import java.lang.invoke.WrongMethodTypeException;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;

import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.worker.binding.*;
import com.microsoft.azure.functions.worker.description.*;
import com.microsoft.azure.functions.worker.reflect.*;
import com.microsoft.azure.functions.rpc.messages.*;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.reflect.TypeUtils;

/**
 * Used to executor of arbitrary Java method in any JAR using reflection.
 * Thread-Safety: Multiple thread.
 */
public class JavaMethodExecutorImpl implements JavaMethodExecutor {

    public void execute(ExecutionContextDataSource executionContextDataSource) throws Exception {
        Object retValue = ParameterResolver.resolveArguments(executionContextDataSource)
                .orElseThrow(() -> new NoSuchMethodException("Cannot locate the method signature with the given input"))
                .invoke(() -> executionContextDataSource.getContainingClass().newInstance());
        executionContextDataSource.getDataStore().setDataTargetValue(BindingDataStore.RETURN_NAME, retValue);
    }
}
