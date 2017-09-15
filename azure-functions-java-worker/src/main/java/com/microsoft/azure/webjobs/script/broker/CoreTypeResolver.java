package com.microsoft.azure.webjobs.script.broker;

import java.lang.reflect.*;

import com.microsoft.azure.serverless.functions.*;
import com.microsoft.azure.serverless.functions.annotation.*;

public class CoreTypeResolver {
    private static boolean isOutputParameter(Type target) {
        if (target instanceof ParameterizedType) {
            target = ((ParameterizedType)target).getRawType();
        }
        return (target instanceof Class<?>) && (OutputBinding.class.isAssignableFrom((Class<?>)target));
    }

    public static Type getOutputParameterArgument(Type outputParamType) {
        if (outputParamType instanceof ParameterizedType) {
            ParameterizedType generics = (ParameterizedType)outputParamType;
            Type[] arguments = generics.getActualTypeArguments();
            if (arguments.length > 0) { return arguments[0]; }
        }
        return Object.class;
    }

    public static boolean isValidOutputType(Type target) {
        if (!isOutputParameter(target)) { return false; }
        target = getOutputParameterArgument(target);
        return !isOutputParameter(target);
    }

    public static boolean isHttpResponse(Type target) {
        return HttpResponseMessage.class.isAssignableFrom(getRuntimeClass(target));
    }

    public static Class<?> getRuntimeClass(Type type) {
        if (type instanceof ParameterizedType) {
            type = ((ParameterizedType)type).getRawType();
        }
        return (Class<?>)type;
    }

    static String getBindingName(Parameter parameter) {
        BindingName bindName = parameter.getAnnotation(BindingName.class);
        if (bindName != null) { return bindName.value(); }
        BlobInput blobIn = parameter.getAnnotation(BlobInput.class);
        if (blobIn != null) { return blobIn.name(); }
        BlobOutput blobOut = parameter.getAnnotation(BlobOutput.class);
        if (blobOut != null) { return blobOut.name(); }
        BlobTrigger blobTrigger = parameter.getAnnotation(BlobTrigger.class);
        if (blobTrigger != null) { return blobTrigger.name(); }
        EventHubOutput eventHubOut = parameter.getAnnotation(EventHubOutput.class);
        if (eventHubOut != null) { return eventHubOut.name(); }
        EventHubTrigger eventHubTrigger = parameter.getAnnotation(EventHubTrigger.class);
        if (eventHubTrigger != null) { return eventHubTrigger.name(); }
        HttpOutput httpOut = parameter.getAnnotation(HttpOutput.class);
        if (httpOut != null) { return httpOut.name(); }
        HttpTrigger httpTrigger = parameter.getAnnotation(HttpTrigger.class);
        if (httpTrigger != null) { return httpTrigger.name(); }
        QueueOutput queueOut = parameter.getAnnotation(QueueOutput.class);
        if (queueOut != null) { return queueOut.name(); }
        QueueTrigger queueTrigger = parameter.getAnnotation(QueueTrigger.class);
        if (queueTrigger != null) { return queueTrigger.name(); }
        ServiceBusQueueOutput serviceBusOut = parameter.getAnnotation(ServiceBusQueueOutput.class);
        if (serviceBusOut != null) { return serviceBusOut.name(); }
        ServiceBusQueueTrigger serviceBusTrigger = parameter.getAnnotation(ServiceBusQueueTrigger.class);
        if (serviceBusTrigger != null) { return serviceBusTrigger.name(); }
        ServiceBusTopicOutput topicOut = parameter.getAnnotation(ServiceBusTopicOutput.class);
        if (topicOut != null) { return topicOut.name(); }
        ServiceBusTopicTrigger topicTrigger = parameter.getAnnotation(ServiceBusTopicTrigger.class);
        if (topicTrigger != null) { return topicTrigger.name(); }
        TableInput tableIn = parameter.getAnnotation(TableInput.class);
        if (tableIn != null) { return tableIn.name(); }
        TableOutput tableOut = parameter.getAnnotation(TableOutput.class);
        if (tableOut != null) { return tableOut.name(); }
        TimerTrigger timerTrigger = parameter.getAnnotation(TimerTrigger.class);
        if (timerTrigger != null) { return timerTrigger.name(); }
        return null;
    }
}
