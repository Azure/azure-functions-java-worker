package com.microsoft.azure.functions.worker.reflect.tests;

import com.microsoft.azure.functions.worker.reflect.JavaClassLoaderProvider;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.*;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class JavaClassLoaderProviderTests {

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
		ClassLoader classLoader1 = new JavaClassLoaderProvider().createClassLoader();
		ClassLoader classLoader2 = new JavaClassLoaderProvider().createClassLoader();
		assertEquals(classLoader1, classLoader2);
	}
}
