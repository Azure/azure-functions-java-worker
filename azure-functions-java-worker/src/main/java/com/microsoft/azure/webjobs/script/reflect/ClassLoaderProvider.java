package com.microsoft.azure.webjobs.script.reflect;

import java.io.IOException;

public interface ClassLoaderProvider {
	
	/*
	 * Adds a search path to be used by the provided class loader
	 */
	void addSearchPath(String path) throws IOException;
	
	/*
	 * Gets the class loader with the required search paths
	 */
	ClassLoader getClassLoader();
}
