package com.microsoft.azure.functions.worker.handler.tests;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

import com.microsoft.azure.functions.worker.broker.JavaFunctionBroker;
import com.microsoft.azure.functions.worker.handler.FunctionEnvironmentReloadRequestHandler;
import com.microsoft.azure.functions.worker.reflect.DefaultClassLoaderProvider;
import org.junit.jupiter.api.Test;

public class FunctionEnvironmentReloadRequestHandlerTest {

	@Test
	public void SetEnv_Succeeds() throws Exception {
		DefaultClassLoaderProvider classLoader = new DefaultClassLoaderProvider();
		JavaFunctionBroker broker = new JavaFunctionBroker(classLoader);
		FunctionEnvironmentReloadRequestHandler envHandler = new FunctionEnvironmentReloadRequestHandler(broker);

		String testSetting = System.getenv("testSetting");
		assertNull(testSetting);
		Map<String, String> existingVariables = System.getenv();
		Map<String, String> newEnvVariables = new HashMap<>();
		newEnvVariables.putAll(existingVariables);
		newEnvVariables.put("testSetting", "testSettingValue");
		envHandler.setEnv(newEnvVariables);
		testSetting = System.getenv("testSetting");
		assertNotNull(testSetting);
		assertEquals( "testSettingValue", testSetting);
	}

	@Test
	public void SetEnv_Null_Succeeds() throws Exception {
		DefaultClassLoaderProvider classLoader = new DefaultClassLoaderProvider();
		JavaFunctionBroker broker = new JavaFunctionBroker(classLoader);
		FunctionEnvironmentReloadRequestHandler envHandler = new FunctionEnvironmentReloadRequestHandler(broker);

		Map<String, String> newEnvVariables = null;
		envHandler.setEnv(newEnvVariables);
	}
}
