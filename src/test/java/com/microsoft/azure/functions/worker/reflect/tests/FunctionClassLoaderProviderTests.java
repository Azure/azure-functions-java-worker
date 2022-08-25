package com.microsoft.azure.functions.worker.reflect.tests;

import static org.junit.Assert.*;
import com.microsoft.azure.functions.worker.reflect.FunctionClassLoaderProvider;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.*;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;


@RunWith(Parameterized.class)
public class FunctionClassLoaderProviderTests {

	@Parameters
	public static Object[] data() {
		return new Object[] { "nospace.txt", "with space.txt" };
	}

	@Parameter
	public String filePath;

	@Rule
	public TemporaryFolder testFolder = new TemporaryFolder();

	@Test
	public void testSingleton() {
		ClassLoader classLoader1 = new FunctionClassLoaderProvider().createClassLoader();
		ClassLoader classLoader2 = new FunctionClassLoaderProvider().createClassLoader();
		assertEquals(classLoader1, classLoader2);
	}

}
