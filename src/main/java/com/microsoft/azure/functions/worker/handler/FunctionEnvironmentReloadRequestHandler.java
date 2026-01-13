package com.microsoft.azure.functions.worker.handler;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;
import java.util.logging.Level;

import com.microsoft.azure.functions.rpc.messages.*;
import com.microsoft.azure.functions.rpc.messages.FunctionEnvironmentReloadResponse.Builder;
import com.microsoft.azure.functions.worker.Application;
import com.microsoft.azure.functions.worker.WorkerLogManager;
import com.microsoft.azure.functions.worker.broker.JavaFunctionBroker;

import static com.microsoft.azure.functions.worker.Constants.JAVA_APPLICATIONINSIGHTS_ENABLE_TELEMETRY;
import static com.microsoft.azure.functions.worker.Constants.JAVA_ENABLE_OPENTELEMETRY;

public class FunctionEnvironmentReloadRequestHandler
		extends MessageHandler<FunctionEnvironmentReloadRequest, FunctionEnvironmentReloadResponse.Builder> {
	public FunctionEnvironmentReloadRequestHandler(JavaFunctionBroker broker) {
		super(StreamingMessage::getFunctionEnvironmentReloadRequest, FunctionEnvironmentReloadResponse::newBuilder,
				FunctionEnvironmentReloadResponse.Builder::setResult,
				StreamingMessage.Builder::setFunctionEnvironmentReloadResponse);

		this.broker = broker;
	}

	public Map<String, String> environmentVariables = new HashMap<>();

	@Override
	String execute(FunctionEnvironmentReloadRequest request, Builder response) throws Exception {
		WorkerLogManager.getSystemLogger().log(Level.INFO, "FunctionEnvironmentReloadRequest received by the Java worker");
		environmentVariables = request.getEnvironmentVariablesMap();
		if (environmentVariables.isEmpty()) {
			return "Ignoring FunctionEnvironmentReloadRequest as newSettings map is empty.";
		}
		
		// Set timezone and get modified environment variables with TZ adjusted if needed
		Map<String, String> modifiedEnvVars = setTimeZone(environmentVariables);
		setEnv(modifiedEnvVars);
		setCapabilities(response, environmentVariables);
		
		return "FunctionEnvironmentReloadRequest completed";
	}

	/*
	 * Sets telemetry capabilities based on environment variables
	 */
	private void setCapabilities(FunctionEnvironmentReloadResponse.Builder response, Map<String, String> environmentVariables) {
		String openTelemetryEnabled = environmentVariables.get(JAVA_ENABLE_OPENTELEMETRY);
		String appInsightsEnabled = environmentVariables.get(JAVA_APPLICATIONINSIGHTS_ENABLE_TELEMETRY);
		
		if (Boolean.parseBoolean(openTelemetryEnabled) || Boolean.parseBoolean(appInsightsEnabled)) {
			response.putCapabilities("WorkerOpenTelemetryEnabled", "true");
			response.putCapabilities("WorkerApplicationInsightsLoggingEnabled", "true");
		}
	}

	/*
	 * Sets the default timezone based on the TZ environment variable.
	 * Returns a modified map where both WEBSITE_TIME_ZONE and TZ are synchronized.
	 */
	private Map<String, String> setTimeZone(Map<String, String> environmentVariables) {
		String websiteTimeZone = environmentVariables.get("WEBSITE_TIME_ZONE");
		String tz = environmentVariables.get("TZ");
		
		// Determine which timezone to use (WEBSITE_TIME_ZONE takes precedence)
		String tzValue = (websiteTimeZone != null && !websiteTimeZone.isEmpty()) ? websiteTimeZone : tz;
		
		if (tzValue == null || tzValue.isEmpty()) {
			return environmentVariables;
		}
		
		// Set the JVM timezone
		try {
			TimeZone timeZone = TimeZone.getTimeZone(tzValue);
			TimeZone.setDefault(timeZone);
			System.setProperty("user.timezone", timeZone.getID());
			WorkerLogManager.getSystemLogger().log(Level.INFO, 
				String.format("Set default timezone to: %s", timeZone.getID()));
		} catch (Exception e) {
			WorkerLogManager.getSystemLogger().log(Level.WARNING, 
				String.format("Failed to set timezone '%s': %s", tzValue, e.getMessage()));
		}
		
		// Synchronize both environment variables to the same value to prevent race conditions
		Map<String, String> modifiedVars = new HashMap<>(environmentVariables);
		modifiedVars.put("WEBSITE_TIME_ZONE", tzValue);
		modifiedVars.put("TZ", tzValue);
		
		return modifiedVars;
	}

	/*
	 * This is a helper utility specifically to reload environment variables if java
	 * language worker is started in standby mode by the functions runtime and
	 * should not be used for other purposes
	 */
	public void setEnv(Map<String, String> newSettings) throws Exception {
		if (newSettings == null || newSettings.isEmpty()) {
			return;
		}
		
		// Update Environment variables in the JVM
		// As an FYI, the JVM creates a copy of the environment variables when it starts.
		// This will edit that copy, not the environment variables for the parent process that started the JVM
		try {
			// update env variable for running JVM on Windows
			Class<?> processEnvironmentClass = Class.forName("java.lang.ProcessEnvironment");
			Field theEnvironmentField = processEnvironmentClass.getDeclaredField("theEnvironment");
			theEnvironmentField.setAccessible(true);
			Map<String, String> env = (Map<String, String>) theEnvironmentField.get(null);
			env.clear();
			env.putAll(newSettings);
			Field theCaseInsensitiveEnvironmentField = processEnvironmentClass
					.getDeclaredField("theCaseInsensitiveEnvironment");
			theCaseInsensitiveEnvironmentField.setAccessible(true);
			Map<String, String> cienv = (Map<String, String>) theCaseInsensitiveEnvironmentField.get(null);
			cienv.clear();
			cienv.putAll(newSettings);
			WorkerLogManager.getSystemLogger().log(Level.INFO,
					"Finished resetting environment variables in the JVM");
		} catch (NoSuchFieldException e) {
			// update env variable for running JVM on Linux
			Class[] classes = Collections.class.getDeclaredClasses();
			Map<String, String> env = System.getenv();
			for (Class cl : classes) {
				if ("java.util.Collections$UnmodifiableMap".equals(cl.getName())) {
					Field field = cl.getDeclaredField("m");
					field.setAccessible(true);
					Object obj = field.get(env);
					Map<String, String> map = (Map<String, String>) obj;
					map.clear();
					map.putAll(newSettings);
				}
			}
		}
	}

	private final JavaFunctionBroker broker;
}