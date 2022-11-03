package com.microsoft.azure.functions.warmup.java;

import com.microsoft.azure.functions.*;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;


/**
 * Azure Functions with HTTP Trigger.
 */
public class Function {

    public HttpResponseMessage run(
            @HttpTrigger(name = "req") HttpRequestMessage<Optional<String>> request, final ExecutionContext context) {
        Set<UUID> set = new HashSet<>();
        set.add(UUID.randomUUID());
        set.add(UUID.randomUUID());
        for (UUID item : set) {
            int id = item.hashCode();
        }
        return request.createResponseBuilder(HttpStatus.OK).body("cx warm up completed").build();
    }
}
