package example.hello;

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import example.hello.model.*;
import org.springframework.cloud.function.adapter.azure.FunctionInvoker;

import java.util.Optional;


public class HelloHandler extends FunctionInvoker<User, Greeting> {

    @FunctionName("hello")
    public HttpResponseMessage execute(
            @HttpTrigger(name = "request", methods = {HttpMethod.GET, HttpMethod.POST}, authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<User>> request,
            ExecutionContext context) {
        User user = request.getBody()
                .filter(u -> u.getName() != null)
                .orElseGet(() -> new User(request.getQueryParameters().getOrDefault("name", "world")));
        context.getLogger().info("Greeting user name: " + user.getName());
        return request
                .createResponseBuilder(HttpStatus.OK)
                .body(handleRequest(user, context))
                .header("Content-Type", "application/json")
                .build();
    }
}
