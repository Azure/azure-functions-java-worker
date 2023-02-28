package example.uppercase;

import java.util.Map;
import java.util.function.Function;

import com.microsoft.azure.functions.ExecutionContext;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.function.json.JsonMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;

@Configuration
public class Config {

	@Bean
	public Function<String, String> echo() {
		return payload -> payload;
	}

	@Bean
	public Function<String, String> uppercase() {
		return payload -> payload.toUpperCase();
	}
}

