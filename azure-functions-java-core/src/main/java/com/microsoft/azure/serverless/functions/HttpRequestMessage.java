package com.microsoft.azure.serverless.functions;

import java.net.URI;
import java.util.Map;

/**
 * An HttpRequestMessage instance is provided to Azure functions that use
 * {@link com.microsoft.azure.serverless.functions.annotation.HttpTrigger HTTP Triggers}. For an example of how to use
 * the http functionality of Azure Functions, refer to the example in the
 * {@link com.microsoft.azure.serverless.functions.annotation.HttpTrigger}
 *
 * @see com.microsoft.azure.serverless.functions.annotation.HttpTrigger
 * @see HttpResponseMessage
 * @param <T> The type of the body content that is expected to be received as part of this HTTP request.
 * @since 1.0.0
 */
public interface HttpRequestMessage<T> {
    /**
     * Returns the URI that was called that resulted in this HTTP request being submitted.
     * @return the URI that was called that resulted in this HTTP request being submitted.
     */
    URI getUri();

    /**
     * Returns the HTTP method name, such as "GET" and "POST".
     * @return the HTTP method name, such as "GET" and "POST".
     */
    String getMethod();

    /**
     * Returns a map of headers that were contained within this HTTP request.
     * @return a map of headers that were contained within this HTTP request.
     */
    Map<String, String> getHeaders();

    /**
     * Returns a map of query parameters that were included with this HTTP request.
     * @return a map of query parameters that were included with this HTTP request.
     */
    Map<String, String> getQueryParameters();

    /**
     * Returns any body content that was included with this HTTP request.
     * @return any body content that was included with this HTTP request.
     */
    T getBody();

    /**
     * Generates a {@link HttpResponseMessage} instance containing the given HTTP status code and response body.
     * Additional headers may be added by calling appropriate methods on {@link HttpResponseMessage}.
     *
     * @param status The HTTP status code to return to the caller of the function.
     * @param body The body content to return to the caller of the function.
     * @param <R> The type of the body, as determined by the return type specified on the function itself.
     * @return An {@link HttpResponseMessage} instance containing the provided status and body content.
     */
    <R> HttpResponseMessage<R> createResponse(int status, R body);
}
