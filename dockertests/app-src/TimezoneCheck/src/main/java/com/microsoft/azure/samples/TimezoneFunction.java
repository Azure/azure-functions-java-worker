package com.microsoft.azure.samples;

import com.microsoft.azure.functions.annotation.*;
import com.microsoft.azure.functions.*;

import java.util.TimeZone;

/**
 * Azure Functions HTTP Trigger that returns the current timezone information.
 * This function is used to verify that the TZ environment variable is correctly
 * applied to the Java runtime.
 */
public class TimezoneFunction {

    @FunctionName("GetTimezone")
    public HttpResponseMessage run(
            @HttpTrigger(
                name = "req",
                methods = {HttpMethod.GET},
                authLevel = AuthorizationLevel.ANONYMOUS
            ) HttpRequestMessage<String> request,
            final ExecutionContext context) {
        
        context.getLogger().info("Processing timezone request.");
        
        // Get the default timezone
        TimeZone defaultTimezone = TimeZone.getDefault();
        String timezoneId = defaultTimezone.getID();
        String tzEnvVar = System.getenv("TZ");
        
        context.getLogger().info(String.format("Default timezone ID: %s, TZ env var: %s", 
            timezoneId, tzEnvVar != null ? tzEnvVar : "not set"));
        
        // Return the timezone ID
        return request.createResponseBuilder(HttpStatus.OK)
                .header("Content-Type", "text/plain")
                .body(timezoneId)
                .build();
    }
}
