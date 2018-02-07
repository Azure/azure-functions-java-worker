package com.microsoft.azure.webjobs.script.reflect;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Kevin Hillinger
 * Default implementation of the class loader provider
 */
public class DefaultClassLoaderProvider implements ClassLoaderProvider {
	public DefaultClassLoaderProvider() {
		searchPaths = Collections.newSetFromMap(new ConcurrentHashMap<URL, Boolean>());
	}
	
	/* 
	 * @see com.microsoft.azure.webjobs.script.reflect.ClassLoaderProvider#getClassLoader()
	 */
	@Override
	public ClassLoader getClassLoader() {
		URL[] urls = new URL[searchPaths.size()];
		searchPaths.toArray(urls);
		
		ClassLoader classLoader = new URLClassLoader(urls);
		return classLoader;
	}

	@Override
	public void addSearchPath(String path) throws IOException {
		File file = new File(path);
	
		if (!file.exists()) {
			throw new IOException("The search path \"" + path + "\" being added does not exist.");
		}
		
		URL pathUrl = file.toURI().toURL();
		searchPaths.add(pathUrl);
	}

	private final Set<URL> searchPaths;
}
