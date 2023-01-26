package example;

import java.util.Map;
import java.util.function.Function;

import com.microsoft.azure.functions.ExecutionContext;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.function.json.JsonMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;

@SpringBootApplication
public class Config {

	public static void main(String[] args) throws Exception {
		SpringApplication.run(Config.class, args);
	}

	@Bean
	public Function<String, String> echo() {
		return payload -> payload;
	}

	@Bean
	public Function<Message<String>, String> uppercase(JsonMapper mapper) {
		return message -> {
			String value = message.getPayload();
			ExecutionContext context = (ExecutionContext) message.getHeaders().get("executionContext");
			try {
				Map<String, String> map = mapper.fromJson(value, Map.class);

				if(map != null)
					map.forEach((k, v) -> map.put(k, v != null ? v.toUpperCase() : null));

				if(context != null)
					context.getLogger().info(new StringBuilder().append("Function: ")
							.append(context.getFunctionName()).append(" is uppercasing ").append(value.toString()).toString());

				return mapper.toString(map);
			} catch (Exception e) {
				e.printStackTrace();
				if(context != null)
					context.getLogger().severe("Function could not parse incoming request");

				return ("Function error: - bad request");
			}
		};
	}

	@Bean
	public Function<Mono<String>, Mono<String>> uppercaseReactive() {
		return mono -> mono.map(value -> value.toUpperCase());
	}

	@Bean
	public Function<Flux<String>, Flux<String>> echoStream() {
		return flux -> flux.map(value -> value.toUpperCase());
	}
}

