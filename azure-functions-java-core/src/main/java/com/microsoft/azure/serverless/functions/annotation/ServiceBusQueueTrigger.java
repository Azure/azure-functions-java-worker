/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.serverless.functions.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <p>Use ServiceBusQueueTrigger annotation to respond to messages from a Service Bus queue. Similar to other annotations,
 * ServiceBusQueueTrigger could be applied to a method parameter with any type (including String, int or any POJO) as long
 * as the parameter is JSON-deserializable.</p>
 *
 * <p>The following example shows a service bus queue trigger which logs the queue message:</p>
 *
 * <pre>{@literal @}FunctionName("sbprocessor")
 * public void serviceBusProcess(
 *    {@literal @}ServiceBusQueueTrigger(name = "msg",
 *                             queueName = "myqueuename",
 *                             connection = "myconnvarname") String message,
 *     final ExecutionContext context
 * ) {
 *     context.getLogger().info(message);
 * }</pre>
 *
 * @since 1.0.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface ServiceBusQueueTrigger {
    String name();

    String dataType() default "";

    String queueName();

    String connection();

    AccessRights access() default AccessRights.MANAGE;
}
