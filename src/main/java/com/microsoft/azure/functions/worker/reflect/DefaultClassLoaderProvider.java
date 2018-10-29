package com.microsoft.azure.functions.worker.reflect;

import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

import org.apache.commons.lang3.exception.*;

import com.microsoft.azure.functions.worker.*;

/**
 * @author Kevin Hillinger Default implementation of the class loader provider
 */
public class DefaultClassLoaderProvider implements ClassLoaderProvider {
	public DefaultClassLoaderProvider() {
		urls = Collections.newSetFromMap(new ConcurrentHashMap<URL, Boolean>());
	}

	/*
	 * @see
	 * com.microsoft.azure.functions.reflect.ClassLoaderProvider#getClassLoader()
	 */
	@Override
	public ClassLoader getClassLoader() {
		URL[] urlsForClassLoader = new URL[urls.size()];
		urls.toArray(urlsForClassLoader);

		URLClassLoader classLoader = new URLClassLoader(urlsForClassLoader);
		Thread.currentThread().setContextClassLoader(classLoader);

		return classLoader;
	}

	@Override
	public void addDirectory(File directory) throws MalformedURLException, IOException {
		if (!directory.exists()) {
			return;
		}

		File[] jarFiles = directory.listFiles(new FileFilter() {
			@Override
			public boolean accept(File file) {
				return file.isFile() && file.getName().endsWith(".jar");
			}
		});

		for (File file : jarFiles) {
			addUrl(file.toURI().toURL());
		}
	}

	@Override
	public void addUrl(URL url) throws IOException {
		if (urls.contains(url)) {
			return;
		}

		if (!isUrlPointingToAFile(url)) {
			throw new IOException("The jar URL \"" + url + "\" being added does not exist.");
		}

		urls.add(url);
		addUrlToSystemClassLoader(url);
	}

	public static boolean isUrlPointingToAFile(URL url) throws UnsupportedEncodingException {
		String decodedPath = URLDecoder.decode(url.getPath(), "UTF-8");
		File file = new File(decodedPath);
		return file.exists();
	}

	private void addUrlToSystemClassLoader(URL url) throws IOException {
		URLClassLoader sysloader = (URLClassLoader) ClassLoader.getSystemClassLoader();
		Class<?> sysclass = URLClassLoader.class;

		try {
			Method method = sysclass.getDeclaredMethod(SYS_LOADER_ADDURL_METHOD_NAME, parameters);
			method.setAccessible(true);
			method.invoke(sysloader, new Object[] { url });
		} catch (Throwable t) {
			throw new IOException("Error adding " + url + " to system classloader");
		}

	}

	private static final String SYS_LOADER_ADDURL_METHOD_NAME = "addURL";
	private static final Class<?>[] parameters = new Class[] { URL.class };
	private final Set<URL> urls;
}
