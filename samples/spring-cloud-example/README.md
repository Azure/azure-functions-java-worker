# Azure Functions with DI adapter

## Common instructions to integrate Azure Functions with Spring Framework

* Use the [Spring Initializer](https://start.spring.io/) to generate a pain, java Spring Boot project without additional dependencies. Set the boot version to `3.0.x`, the build to `Maven` and the packaging to `Jar`.

* Add the `spring-cloud-function-adapter-azure` POM dependency:

	```xml
	<dependency>
		<groupId>org.springframework.cloud</groupId>
		<artifactId>spring-cloud-function-adapter-azure</artifactId>
		<version>4.0.0</version>
	</dependency>
	```
	Having the adapter on the classpath activates the Azure Java Worker integration.

* Implement the [Azure Java Functions](https://learn.microsoft.com/en-us/azure/azure-functions/functions-reference-java?tabs=bash%2Cconsumption#java-function-basics) as `@FunctionName` annotated methods:

	```java
	import com.microsoft.azure.functions.*;
	import com.microsoft.azure.functions.annotation.AuthorizationLevel;
	import com.microsoft.azure.functions.annotation.FunctionName;
	import com.microsoft.azure.functions.annotation.HttpTrigger;
	import example.hello.model.*;
	import org.springframework.beans.factory.annotation.Autowired;
	import org.springframework.stereotype.Component;
	
	import java.util.Optional;
	
	@Component
	public class HelloHandler {
	
		@Autowired
		private Hello hello;
	
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
					.body(hello.apply(user))
					.header("Content-Type", "application/json")
					.build();
		}
	}
	```
	- The `@FunctionName` annotated methods represent the Azure Function implementations.
	- The class must be marked with the Spring `@Component` annotation.
	- You can use any Spring mechanism to auto-wire the Spring beans used for the function implementation.

* Add the `host.json` configuration under the `src/main/resources` folder:

	```json
	{
		"version": "2.0",
		"extensionBundle": {
			"id": "Microsoft.Azure.Functions.ExtensionBundle",
			"version": "[4.*, 5.0.0)"
		}
	}
	```

* When bootstrapped as Spring Boot project make sure to either disable the `spring-boot-maven-plugin` plugin or cover it into `thin-layout`:

	```xml
	<plugin>
		<groupId>org.springframework.boot</groupId>
		<artifactId>spring-boot-maven-plugin</artifactId>
		<dependencies>
			<dependency>
				<groupId>org.springframework.boot.experimental</groupId>
				<artifactId>spring-boot-thin-layout</artifactId>
				<version>${spring-boot-thin-layout.version}</version>
			</dependency>
		</dependencies>
	</plugin>
	```
	Since Azure Functions requires a specific, custom, Jar packaging we have to disable SpringBoot one.

* Add the `start-class` POM property to point to your main (e.g. SpringApplication) class.
	```xml
	<properties>
		<java.version>17</java.version>
		<start-class>YOUR MAIN CLASS</start-class>
		...
	</properties>
	```

* Add the `azure-functions-maven-plugin` to your POM configuration. A sample configuration would look like this.

	```xml
	<plugin>
		<groupId>com.microsoft.azure</groupId>
		<artifactId>azure-functions-maven-plugin</artifactId>
		<version>1.22.0 or higher</version>

		<configuration>
			<appName>YOUR-AZURE-FUNCTION-APP-NAME</appName>
			<resourceGroup>YOUR-AZURE-FUNCTION-RESOURCE-GROUP</resourceGroup>
			<region>YOUR-AZURE-FUNCTION-APP-REGION</region>
			<appServicePlanName>YOUR-AZURE-FUNCTION-APP-SERVICE-PLANE-NAME</appServicePlanName>
			<pricingTier>YOUR-AZURE-FUNCTION-PRICING-TIER</pricingTier>

			<hostJson>${project.basedir}/src/main/resources/host.json</hostJson>

			<runtime>
				<os>linux</os>
				<javaVersion>11</javaVersion>
			</runtime>

			<funcPort>7072</funcPort>

			<appSettings>
				<property>
					<name>FUNCTIONS_EXTENSION_VERSION</name>
					<value>~4</value>
				</property>
			</appSettings>
		</configuration>
		<executions>
			<execution>
				<id>package-functions</id>
				<goals>
					<goal>package</goal>
				</goals>
			</execution>
		</executions>
	</plugin>
	```
	- Set the AZURE subscription configuration such as app name, resource group, region, service plan, pricing Tier
    - Runtime configuration:
		- [Java Versions](https://learn.microsoft.com/en-us/azure/azure-functions/functions-reference-java?tabs=bash%2Cconsumption#java-versions)
		- Specify [Deployment OS](https://learn.microsoft.com/en-us/azure/azure-functions/functions-reference-java?tabs=bash%2Cconsumption#specify-the-deployment-os)

* Build the project:

	```
	mvn clean package
	```

## Running Locally

NOTE: To run locally on top of `Azure Functions`, and to deploy to your live Azure environment, you will need `Azure Functions Core Tools` installed along with the Azure CLI (see [here](https://docs.microsoft.com/en-us/azure/azure-functions/create-first-function-cli-java?tabs=bash%2Cazure-cli%2Cbrowser#configure-your-local-environment)).

NOTE: [Azure Functions Core Tools](https://github.com/Azure/azure-functions-core-tools) version `4.0.5030` or newer is required!

For some configuration you would need the [Azurite emulator](https://learn.microsoft.com/en-us/azure/storage/common/storage-use-emulator) as well.

Then build and run the sample:

```
mvn clean package
mvn azure-functions:run
```

## Running on Azure

Make sure you are logged in your Azure account.
```
az login
```

Build and deploy

```
mvn clean package
mvn azure-functions:deploy
```

## Debug locally

Run the function in debug mode.
```
mvn azure-functions:deploy -DenableDebug
```

VS Code remote debug configuration:

	```json
	{
		"version": "0.2.0",
		"configurations": [
			{
				"type": "java",
				"name": "Attach to Remote Program",
				"request": "attach",
				"hostName": "localhost",
				"port": "5005"
			},
	}

	```