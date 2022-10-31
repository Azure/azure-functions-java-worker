package com.microsoft.azure.functions.worker.binding;

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.internal.spi.middleware.MiddlewareContext;
import com.microsoft.azure.functions.rpc.messages.ParameterBinding;
import com.microsoft.azure.functions.rpc.messages.TypedData;
import com.microsoft.azure.functions.spi.inject.FunctionInstanceInjector;
import com.microsoft.azure.functions.worker.WorkerLogManager;
import com.microsoft.azure.functions.worker.broker.MethodBindInfo;
import com.microsoft.azure.functions.worker.broker.ParamBindInfo;

import java.lang.annotation.Annotation;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

public final class ExecutionContextDataSource extends DataSource<ExecutionContext> implements MiddlewareContext {
    private final String invocationId;
    private final TraceContext traceContext;
    private final RetryContext retryContext;
    private final Logger logger;
    private final String funcname;
    private final BindingDataStore dataStore;
    private final MethodBindInfo methodBindInfo;
    private final Class<?> containingClass;

    /*
    Key is the name defined on customer function parameters. For ex:
    @HttpTrigger(
            name = "req",
            methods = {HttpMethod.GET, HttpMethod.POST},
            authLevel = AuthorizationLevel.ANONYMOUS)
    HttpRequestMessage<Optional<String>> request,
    Here name will be the "req".

    Value is java.lang.reflect.Parameter type
     */
    private final Map<String, Parameter> parameterDefinitions;

    // currently the values are only Strings (resolved from grpc String values)
    // but planning to support other types in the future
    private final Map<String, Object> parameterValues;

    // these are parameters provided by the middleware, which will override the host provided parameter values
    // currently the values are only Strings, but planning to support other types in the future
    private final Map<String, Object> middlewareParameterValues = new HashMap<>();
    private Object returnValue;

    private final FunctionInstanceInjector functionInstanceInjector;

    //TODO: refactor class to have subclass dedicate to middleware to make logics clean
    private static final DataOperations<ExecutionContext, Object> EXECONTEXT_DATA_OPERATIONS = new DataOperations<>();
    static {
        EXECONTEXT_DATA_OPERATIONS.addGenericOperation(ExecutionContext.class, DataOperations::generalAssignment);
    }

    public ExecutionContextDataSource(String invocationId, TraceContext traceContext, RetryContext retryContext,
                                      String funcname, BindingDataStore dataStore, MethodBindInfo methodBindInfo,
                                      Class<?> containingClass, List<ParameterBinding> parameterBindings, FunctionInstanceInjector functionInstanceInjector){
        super(null, null, EXECONTEXT_DATA_OPERATIONS);
        this.invocationId = invocationId;
        this.traceContext = traceContext;
        this.retryContext = retryContext;
        this.logger = WorkerLogManager.getInvocationLogger(invocationId);
        this.funcname = funcname;
        this.dataStore = dataStore;
        this.methodBindInfo = methodBindInfo;
        this.containingClass = containingClass;
        this.parameterDefinitions = getParameterDefinitions(methodBindInfo);
        this.parameterValues = resolveParameterValuesForMiddleware(parameterBindings);
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

    private static Map<String, Parameter> getParameterDefinitions(MethodBindInfo methodBindInfo){
        Map<String, Parameter> map = new HashMap<>();
        for (ParamBindInfo paramBindInfo : methodBindInfo.getParams()) {
            map.put(paramBindInfo.getName(), paramBindInfo.getParameter());
        }
        return map;
    }


    //TODO: leverage stream to do the check
    @Override
    public String getParameterName(String annotationSimpleClassName){
        for (Map.Entry<String, Parameter> entry : this.parameterDefinitions.entrySet()){
            if (hasAnnotation(entry.getValue(), annotationSimpleClassName)){
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

    // TODO: Refactor the code in V5 to make resolve arguments logics before middleware invocation.
    // for now only supporting String parameter values mapped to String values
    private static Map<String, Object> resolveParameterValuesForMiddleware(List<ParameterBinding> parameterBindings){
        Map<String, Object> map = new HashMap<>();
        for (ParameterBinding parameterBinding : parameterBindings) {
            TypedData typedData = parameterBinding.getData();
            if (typedData.getDataCase() == TypedData.DataCase.STRING){
                map.put(parameterBinding.getName(), typedData.getString());
            }
        }
        return map;
    }

    @Override
    public Object getParameterValue(String name) {
        return this.parameterValues.get(name);
    }

    @Override
    public void updateParameterValue(String name, Object value) {
        this.middlewareParameterValues.put(name, value);
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

    public Optional<BindingData> getBindingData(String paramName, Type paramType) {
        Object inputValue = this.middlewareParameterValues.get(paramName);
        if (inputValue != null) {
            return Optional.of(new BindingData(inputValue));
        }else{
            return dataStore.getDataByName(paramName, paramType);
        }
    }
}