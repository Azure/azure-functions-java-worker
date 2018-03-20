package com.microsoft.azure.serverless.functions;

/**
 * An HttpResponseMessage instance is returned by Azure Functions methods that are triggered by an
 * {@link com.microsoft.azure.serverless.functions.annotation.HttpTrigger}.
 *
 * @see com.microsoft.azure.serverless.functions.annotation.HttpTrigger
 * @see HttpRequestMessage
 * @param <T> The type of the body, as determined by the return type specified on the function itself.
 * @since 1.0.0
 */
public interface HttpResponseMessage<T> {

    /**
     * Returns the status code set on the HttpResponseMessage instance.
     * @return the status code set on the HttpResponseMessage instance.
     */
    int getStatus();

    /**
     * Sets the status code on the HttpResponseMessage instance.
     * @param status An HTTP status code representing the outcome of the HTTP request.
     */
    void setStatus(int status);

    /**
     * Adds a (key,value) header to the response.
     * @param key The key of the header value.
     * @param value The value of the header value.
     */
    void addHeader(String key, String value);

    /**
     * Returns a header value for the given key.
     * @param key The key for which the header value is sought.
     * @return Returns the value if the key has previously been added, or null if it has not.
     */
    String getHeader(String key);

    /**
     * Returns the body of the HTTP response.
     * @return the body of the HTTP response.
     */
    T getBody();

    /**
     * Sets the body of the HTTP response.
     * @param body The body of the HTTP response
     */
    void setBody(T body);
}
