package example;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import java.util.Optional;

import org.springframework.cloud.function.adapter.azure.FunctionInvoker;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

public class UppercaseHandler extends FunctionInvoker<Message<String>, String> {

	@FunctionName("uppercase")
	public String execute(
		@HttpTrigger(
			name = "req",
			methods = {HttpMethod.GET, HttpMethod.POST},
			authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
		ExecutionContext context
	) {
		context.getLogger().warning("Using Java (" + System.getProperty("java.version") + ")");
		Message<String> message = MessageBuilder.withPayload(request.getBody().get())
			.copyHeaders(request.getHeaders()).build();
		return handleRequest(message, context);
	}
}
