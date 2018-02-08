package com.microsoft.azure.webjobs.script.reflect;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

public interface ClassLoaderProvider {
	
	/*
	 * Adds a search path to be used by the provided class loader
	 */
	void addUrl(URL url) throws IOException;
	
	/*
	 * Adds all jar-files found in a directory (NOT recursive)
	 */
	void addDirectory(File directory);
	
	/*
	 * Gets the class loader with the required search paths
	 */
	ClassLoader getClassLoader();
}
