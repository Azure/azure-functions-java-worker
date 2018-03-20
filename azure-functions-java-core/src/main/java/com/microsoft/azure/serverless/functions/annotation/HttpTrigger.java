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
 * {@literal @}FunctionName("hello")
 *  public HttpResponseMessage&lt;String&gt; helloFunction(
 *    {@literal @}HttpTrigger(name = "req", methods = {"get"}, authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage&lt;Optional&lt;String&gt;&gt; request) {
 *     ....
 *  }</pre>
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
     * The variable name used in function code for the request or request body.
     * @return The variable name used in function code for the request or request body.
     */
    String name();

    String dataType() default "";

    /**
     * <p>Defines the route template, controlling which request URLs your function will respond to. The default value if
     * no route is provided is the function name specified in the {@link FunctionName} annotation, applied to each
     * Azure Function.</p>
     *
     * <p>By default when you create a function for an HTTP trigger, or WebHook, the function is addressable with a
     * route of the form {@code http://<yourapp>.azurewebsites.net/api/<funcname>}. You can customize this route using
     * this route property. For example, a route of {@code "products/{category:alpha}/{id:int}"} would mean that the
     * function is now addressable with the following route instead of the original route:
     * {@code http://<yourapp>.azurewebsites.net/api/products/electronics/357}, which allows the function code to support
     * two parameters in the address: category and id.</p>
     *
     * <p>For more details on the route syntax, refer to the
     * <a href="https://docs.microsoft.com/en-us/aspnet/web-api/overview/web-api-routing-and-actions/attribute-routing-in-web-api-2#constraints">
     *     online documentation</a>.</p>
     *
     * @return The route template to use for the annotated function.
     */
    String route() default "";

    /**
     * An array of the HTTP methods to which the function responds. If not specified, the function responds to all HTTP
     * methods.
     *
     * @return An array containing all valid HTTP methods.
     */
    String[] methods() default {};

    /**
     * Determines what keys, if any, need to be present on the request in order to invoke the function. The authorization
     * level can be one of the following values:
     *
     * <ul>
     *     <li><strong>anonymous</strong>: No API key is required.</li>
     *     <li><strong>function</strong>: A function-specific API key is required. This is the default value if none is provided.</li>
     *     <li><strong>admin</strong>: The master key is required.</li>
     * </ul>
     *
     * <p>For more information, see the
     * <a href="https://docs.microsoft.com/azure/azure-functions/functions-bindings-http-webhook#authorization-keys">documentation about authorization keys</a>.</p>
     *
     * @return An {@link AuthorizationLevel} value representing the level required to access the function.
     */
    AuthorizationLevel authLevel() default AuthorizationLevel.FUNCTION;

    /**
     * Configures the HTTP trigger to act as a webhook receiver for the specified provider. Don't set the methods
     * property if you set this property. The webhook type can be one of the following values:
     *
     * <ul>
     *     <li><strong>genericJson</strong>: A general-purpose webhook endpoint without logic for a specific provider.
     *     This setting restricts requests to only those using HTTP POST and with the {@code application/json} content
     *     type.</li>
     *     <li><strong>github</strong>: The function responds to
     *     <a href="https://developer.github.com/webhooks/">GitHub webhooks</a>. Do not use the {@link #authLevel()}
     *     property with GitHub webhooks.</li>
     *     <li><strong>slack</strong>: The function responds to
     *     <a href="https://api.slack.com/outgoing-webhooks">Slack webhooks</a>. Do not use the {@link #authLevel()}
     *     property with Slack webhooks.</li>
     * </ul>
     *
     * @return A string representing the desired webhook type.
     */
    String webHookType() default "";
}
