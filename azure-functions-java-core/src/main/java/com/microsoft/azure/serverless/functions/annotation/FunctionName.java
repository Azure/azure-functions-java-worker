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
 * <p>The {@code FunctionName} annotation is used to specify to the Azure Functions tooling what name is to be applied
 * to the associated function when the function is deployed onto Azure. This becomes the endpoint (in the case of an
 * {@link HttpTrigger http triggered} function, for example, but more generally it is what is shown to users in the
 * Azure Portal, so a succinct and understandable function name is useful.</p>
 *
 * <p>An example of how the {@code FunctionName} annotation is shown in the code snippet below. Note that it is applied
 * to the function that will be called by Azure, based on the specified trigger (in the code below it is a
 * {@link HttpTrigger}).</p>
 *
 * <pre>
 * {@literal @}FunctionName("redirect")
 *  public HttpResponseMessage&lt;String&gt; redirectFunction(
 *    {@literal @}HttpTrigger(name = "req",
 *                            methods = {"get"}, authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage&lt;Optional&lt;String&gt;&gt; request) {
 *     ....
 *  }</pre>
 *
 * @since 1.0.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface FunctionName {
    /**
     * The name of the function.
     * @return The name of the function.
     */
    String value();
}
