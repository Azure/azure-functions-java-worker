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
 * <p>The following example shows an event hub trigger which logs the message:</p>
 *
 * <pre>{@literal @}FunctionName("ehprocessor")
 * public void eventHubProcessor(
 *    {@literal @}EventHubTrigger(name = "msg",
 *                      eventHubName = "myeventhubname",
 *                      connection = "myconnvarname") String message,
 *     final ExecutionContext context
 * ) {
 *     context.getLogger().info(message);
 * }</pre>
 *
 * @since 1.0.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface EventHubTrigger {
    String name();

    String dataType() default "";

    String eventHubName();

    String consumerGroup() default "$Default";

    String connection();
}
