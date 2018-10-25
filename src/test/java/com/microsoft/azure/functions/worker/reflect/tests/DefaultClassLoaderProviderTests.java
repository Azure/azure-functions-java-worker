package com.microsoft.azure.functions.worker.reflect.tests;

import java.io.*;
import java.net.*;

import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.*;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import com.microsoft.azure.functions.worker.reflect.DefaultClassLoaderProvider;

@RunWith(Parameterized.class)
public class DefaultClassLoaderProviderTests {

	@Parameters
	public static Object[] data() {
		return new Object[] { "nospace.txt", "with space.txt" };
	}

	@Parameter
	public String filePath;
	
	@Rule
	public TemporaryFolder testFolder = new TemporaryFolder();

	@Test
	public void isUrlPointingToAFile_Returns_True() throws IOException {				
		File createdFile = testFolder.newFile(filePath);			
		boolean fileExists = DefaultClassLoaderProvider.isUrlPointingToAFile(createdFile.toURI().toURL());
		assertTrue(fileExists);
	}
	
	@Test
	public void isUrlPointingToAFile_False() throws IOException {	
		String dummyFile = "filedoesnotexist.txt";
		URL jarUrl = new File(dummyFile).toURI().toURL();		
		boolean fileExists = DefaultClassLoaderProvider.isUrlPointingToAFile(jarUrl);
		assertFalse(fileExists);
	}
}
