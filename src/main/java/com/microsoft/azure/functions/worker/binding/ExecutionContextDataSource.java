package com.microsoft.azure.functions.worker.binding;

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.internal.spi.middleware.MiddlewareContext;
import com.microsoft.azure.functions.spi.inject.FunctionInstanceInjector;
import com.microsoft.azure.functions.worker.WorkerLogManager;
import com.microsoft.azure.functions.worker.broker.MethodBindInfo;
import com.microsoft.azure.functions.worker.chain.ExecutionParameter;

import java.lang.annotation.Annotation;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public final class ExecutionContextDataSource extends DataSource<ExecutionContext> implements MiddlewareContext {
    public final static String EXECUTION_CONTEXT = "ExecutionContext";
    private final String invocationId;
    private final TraceContext traceContext;
    private final RetryContext retryContext;
    private final Logger logger;
    private final String funcname;
    private final BindingDataStore dataStore;
    private final MethodBindInfo methodBindInfo;
    private final Class<?> containingClass;
    private Object returnValue;
    private final FunctionInstanceInjector functionInstanceInjector;

    //TODO: refactor class to have subclass dedicate to middleware to make logics clean
    private static final DataOperations<ExecutionContext, Object> EXECONTEXT_DATA_OPERATIONS = new DataOperations<>();
    static {
        EXECONTEXT_DATA_OPERATIONS.addGenericOperation(ExecutionContext.class, DataOperations::generalAssignment);
    }

    public final Map<String, ExecutionParameter> argumentsMap = new LinkedHashMap<>();

    public ExecutionContextDataSource(String invocationId, TraceContext traceContext, RetryContext retryContext,
                                      String funcname, BindingDataStore dataStore, MethodBindInfo methodBindInfo,
                                      Class<?> containingClass, FunctionInstanceInjector functionInstanceInjector){
        super(null, null, EXECONTEXT_DATA_OPERATIONS);
        this.invocationId = invocationId;
        this.traceContext = traceContext;
        this.retryContext = retryContext;
        this.logger = WorkerLogManager.getInvocationLogger(invocationId);
        this.funcname = funcname;
        this.dataStore = dataStore;
        this.methodBindInfo = methodBindInfo;
        this.containingClass = containingClass;
        this.functionInstanceInjector = functionInstanceInjector;
        this.setValue(this);
    }

    @Override
    public String getInvocationId() { return this.invocationId; }

    @Override
    public Logger getLogger() { return this.logger; }

    @Override
    public TraceContext getTraceContext() { return this.traceContext; }

    @Override
    public RetryContext getRetryContext() { return this.retryContext; }

    @Override
    public String getFunctionName() { return this.funcname; }

    public BindingDataStore getDataStore() {
        return dataStore;
    }

    public MethodBindInfo getMethodBindInfo() {
        return methodBindInfo;
    }

    public Object getFunctionInstance() throws Exception {
        return this.functionInstanceInjector.getInstance(containingClass);
    }

    public Object[] getArguments() {
        return this.argumentsMap.values().stream().map(ExecutionParameter::getBindingData).toArray();
    }

    public void addExecutionParameter(String name, ExecutionParameter executionParameter){
        this.argumentsMap.put(name, executionParameter);
    }

    //TODO: leverage stream to do the check
    @Override
    public String getParameterName(String annotationSimpleClassName){
        for (Map.Entry<String, ExecutionParameter> entry : this.argumentsMap.entrySet()){
            if (hasAnnotation(entry.getValue().getParameter(), annotationSimpleClassName)){
                return entry.getKey();
            }
        }
        return null;
    }

    private static boolean hasAnnotation(Parameter parameter, String annotationSimpleClassName){
        Annotation[] annotations = parameter.getAnnotations();
        for (Annotation annotation : annotations) {
            if(annotation.annotationType().getSimpleName().equals(annotationSimpleClassName)){
                return true;
            }
        }
        return false;
    }

    @Override
    public Object getParameterValue(String name) {
        return this.argumentsMap.get(name).getBindingData();
    }

    @Override
    public void updateParameterValue(String name, Object value) {
        this.argumentsMap.get(name).setBindingData(value);
    }

    @Override
    public Object getReturnValue() {
        return this.returnValue;
    }

    @Override
    public void updateReturnValue(Object returnValue) {
        this.returnValue = returnValue;
        // set the return value that will be sent back to host
        this.dataStore.setDataTargetValue(BindingDataStore.RETURN_NAME, this.returnValue);
    }
}