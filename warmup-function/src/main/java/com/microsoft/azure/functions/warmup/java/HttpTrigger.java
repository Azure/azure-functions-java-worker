/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.functions.warmup.java;

import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.FunctionName;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <p>
 * The HttpTrigger annotation is applied to Azure functions that will be triggered by a call to the
 * HTTP endpoint that the function is located at. The HttpTrigger annotation should be applied to a
 * method parameter of one of the following types:
 * </p>
 *
 * <ul>
 * <li>{@link com.microsoft.azure.functions.HttpRequestMessage HttpRequestMessage&lt;T&gt;}</li>
 * <li>Any native Java types such as int, String, byte[]</li>
 * <li>Nullable values using Optional&lt;T&gt;</li>
 * <li>Any POJO type</li>
 * </ul>
 *
 * <p>
 * For example:
 * </p>
 *
 * <pre>
 * {@literal @}FunctionName("hello")
 *  public HttpResponseMessage&lt;String&gt; helloFunction(
 *    {@literal @}HttpTrigger(name = "req",
 *                  methods = {HttpMethod.GET},
 *                  authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage&lt;Optional&lt;String&gt;&gt; request
 *  ) {
 *     ....
 *  }
 * </pre>
 *
 * <p>
 * In this code snippet you will observe that we have a function annotated with
 * {@code @FunctionName("hello")}, which indicates that this function will be available at the
 * endpoint /api/hello. The name of the method itself, in this case {@code helloFunction} is
 * irrelevant for all intents and purposes related to Azure Functions. Note however that the method
 * return type is {@link com.microsoft.azure.functions.HttpResponseMessage}, and that the first
 * argument into the function is an {@link com.microsoft.azure.functions.HttpRequestMessage} with
 * generic type {@code Optional<String>}. This indicates that the body of the request will
 * potentially contain a String value.
 * </p>
 *
 * <p>
 * Most important of all however is the {@code @HttpTrigger} annotation that has been applied to
 * this argument. In this annotation you'll note that it has been given a name, as well as told what
 * type of requests it supports (in this case, only HTTP GET requests), and that the
 * {@link AuthorizationLevel} is anonymous, allowing access to anyone who can call the endpoint.
 * </p>
 *
 * <p>
 * The {@code HttpTrigger} can be further customised by providing a custom {@link #route()}, which
 * allows for custom endpoints to be specified, and for these endpoints to be parameterized with
 * arguments being bound to arguments provided to the function at runtime.
 * </p>
 *
 * <p>
 * The following example shows a Java function that looks for a name parameter either in the query
 * string (HTTP GET) or the body (HTTP POST) of the HTTP request. Notice that the return value is
 * used for the output binding, but a return value attribute isn't required.
 * </p>
 * 
 * <pre>
 * {@literal @}FunctionName("readHttpName")
 *  public String readName(
 *    {@literal @}HttpTrigger(name = "req", 
 *          methods = {HttpMethod.GET, HttpMethod.POST}, authLevel = AuthorizationLevel.ANONYMOUS)
 *          final HttpRequestMessage&lt;Optional&lt;String&gt;&gt; request) {
 *       String name = request.getBody().orElseGet(() -&gt; request.getQueryParameters().get("name"));
 *       return name == null ?
 *              "Please pass a name on the query string or in the request body" :
 *              "Hello " + name;
 *  }
 * </pre>
 * 
 * @see com.microsoft.azure.functions.HttpRequestMessage
 * @see com.microsoft.azure.functions.HttpResponseMessage
 * @since 1.0.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface HttpTrigger {
  /**
   * The variable name used in function code for the request or request body.
   * 
   * @return The variable name used in function code for the request or request body.
   */
  String name();

  /**
   * <p>
   * Defines how Functions runtime should treat the parameter value. Possible values are:
   * </p>
   * <ul>
   * <li>"": get the value as a string, and try to deserialize to actual parameter type like
   * POJO</li>
   * <li>string: always get the value as a string</li>
   * <li>binary: get the value as a binary data, and try to deserialize to actual parameter type
   * byte[]</li>
   * </ul>
   * 
   * @return The dataType which will be used by the Functions runtime.
   */
  String dataType() default "";

  /**
   * <p>
   * Defines the route template, controlling which request URLs your function will respond to. The
   * default value if no route is provided is the function name specified in the
   * {@link FunctionName} annotation, applied to each Azure Function.
   * </p>
   *
   * <p>
   * By default when you create a function for an HTTP trigger, or WebHook, the function is
   * addressable with a route of the form
   * {@code http://&lt;yourapp&gt;.azurewebsites.net/api/&lt;funcname&gt;}. You can customize this
   * route using this route property. For example, a route of
   * {@code "products/{category:alpha}/{id:int}"} would mean that the function is now addressable
   * with the following route instead of the original route:
   * {@code http://&lt;yourapp&gt;.azurewebsites.net/api/products/electronics/357}, which allows the
   * function code to support two parameters in the address: category and id. By specifying the
   * route in this way, developers can then add the additional route arguments as arguments into the
   * function by using the {@link BindingName} annotation. For example:
   * </p>
   *
   * <pre>
   * {@literal @}FunctionName("routeTest")
   *  public HttpResponseMessage&lt;String&gt; routeTest(
   *      {@literal @}HttpTrigger(name = "req",
   *                    methods = {HttpMethod.GET},
   *                    authLevel = AuthorizationLevel.ANONYMOUS,
   *                    route = "products/{category:alpha}/{id:int}") 
   *                    HttpRequestMessage&lt;Optional&lt;String&gt;&gt; request,
   *      {@literal @}BindingName("category") String category,
   *      {@literal @}BindingName("id") int id,
   *       final ExecutionContext context
   *  ) {
   *           ....
   *           context.getLogger().info("We have " + category + " with id " + id);
   *           ....
   *  }
   * </pre>
   *
   * <p>
   * For more details on the route syntax, refer to the <a href=
   * "https://docs.microsoft.com/en-us/aspnet/web-api/overview/web-api-routing-and-actions">
   *  online documentation</a>.
   * </p>
   *
   * @return The route template to use for the annotated function.
   */
  String route() default "";

  /**
   * An array of the HTTP methods to which the function responds. If not specified, the function
   * responds to all HTTP methods.
   *
   * @return An array containing all valid HTTP methods.
   */
  HttpMethod[] methods() default {};

  /**
   * <p>
   * Determines what keys, if any, need to be present on the request in order to invoke the
   * function. The authorization level can be one of the following values:
   * </p>
   *
   * <ul>
   * <li><strong>anonymous</strong>: No API key is required.</li>
   * <li><strong>function</strong>: A function-specific API key is required. This is the default
   * value if none is provided.</li>
   * <li><strong>admin</strong>: The master key is required.</li>
   * </ul>
   *
   * <p>
   * For more information, see the <a href=
   * "https://docs.microsoft.com/azure/azure-functions/functions-bindings-http-webhook#authorization-keys">documentation
   * about authorization keys</a>.
   * </p>
   *
   * @return An {@link AuthorizationLevel} value representing the level required to access the
   *         function.
   */
  AuthorizationLevel authLevel() default AuthorizationLevel.FUNCTION;
}
