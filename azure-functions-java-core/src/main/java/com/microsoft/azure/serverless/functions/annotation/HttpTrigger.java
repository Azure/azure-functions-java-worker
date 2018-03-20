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
 * <p>The HttpTrigger annotation is applied to Azure functions that will be triggered by a call to the HTTP endpoint that
 * the function is located at. The HttpTrigger annotation should be applied to a method parameter of type
 * {@link com.microsoft.azure.serverless.functions.HttpRequestMessage}. For example:</p>
 *
 * <pre>
{@literal @}FunctionName("hello")
 public HttpResponseMessage&lt;String&gt; helloFunction(
    {@literal @}HttpTrigger(name = "req", methods = {"get"}, authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage&lt;Optional&lt;String&gt;&gt; request,
     final ExecutionContext context) {
     ....
 }</pre>
 *
 * <p>In this code snippet you will observe that we have a function annotated with {@code @FunctionName("hello")},
 * which indicates that this function will be available at the endpoint /api/hello. The name of the method itself, in
 * this case {@code helloFunction} is irrelevant for all intents and purposes related to Azure Functions. Note however
 * that the method return type is {@link com.microsoft.azure.serverless.functions.HttpResponseMessage}, and
 * that the first argument into the function is an {@link com.microsoft.azure.serverless.functions.HttpRequestMessage}
 * with generic type {@code Optional<String>}. This indicates that the body of the request will potentially contain
 * a String value.</p>
 *
 * <p>Most important of all however is the {@code @HttpTrigger} annotation that has been applied
 * to this argument. In this annotation you'll note that it has been given a name, as well as told what type of requests
 * it supports (in this case, only HTTP GET requests), and that the {@link AuthorizationLevel} is anonymous, allowing
 * access to anyone who can call the endpoint.</p>
 *
 * @see com.microsoft.azure.serverless.functions.HttpRequestMessage
 * @see com.microsoft.azure.serverless.functions.HttpResponseMessage
 * @since 1.0.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface HttpTrigger {
    /**
     * @return The name of the HTTP trigger.
     */
    String name();

    String dataType() default "";

    String route() default "";

    String[] methods() default {};

    AuthorizationLevel authLevel() default AuthorizationLevel.FUNCTION;

    String webHookType() default "";
}
