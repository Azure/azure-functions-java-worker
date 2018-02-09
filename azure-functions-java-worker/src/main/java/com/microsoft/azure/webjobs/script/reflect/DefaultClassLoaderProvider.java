package com.microsoft.azure.webjobs.script.reflect;

import java.io.*;
import java.lang.reflect.Method;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * @author Kevin Hillinger
 * Default implementation of the class loader provider
 */
public class DefaultClassLoaderProvider implements ClassLoaderProvider {
	public DefaultClassLoaderProvider() {
		urls = Collections.newSetFromMap(new ConcurrentHashMap<URL, Boolean>());
	}
	
	/* 
	 * @see com.microsoft.azure.webjobs.script.reflect.ClassLoaderProvider#getClassLoader()
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
	public void addDirectory(File directory) {
		if (!directory.exists()) {
			return;
		}
		
		File[] jarFiles = directory.listFiles(new FileFilter() {
		    @Override
		    public boolean accept(File file) {
		        return file.isFile() && file.getName().endsWith(".jar");
		    }
		});
		
		try {
			for (File file : jarFiles) {
				addUrl(file.toURI().toURL());
			}
		} catch (Exception e) {
			//todo: log
		} 
	}
	
	@Override
	public void addUrl(URL url) throws IOException  {
		if (urls.contains(url)) {
			return;
		}
		
		if (!isUrlPointingToAFile(url)) {
			throw new IOException("The jar URL \"" + url + "\" being added does not exist.");
		}
		
		urls.add(url);
		addUrlToSystemClassLoader(url);
	}
	
	private boolean isUrlPointingToAFile(URL url) {
		File file = new File(url.getPath());
		return file.exists();
	}
	
	private void addUrlToSystemClassLoader(URL url) throws IOException
    {
        URLClassLoader sysloader = (URLClassLoader)ClassLoader.getSystemClassLoader();
        Class<?> sysclass = URLClassLoader.class;
        
        try
        {
            Method method = sysclass.getDeclaredMethod(systemClassLoaderAddUrlMethodName, parameters);
            method.setAccessible(true);
            method.invoke(sysloader, new Object[] { url });
        }
        catch (Throwable t)
        {
            throw new IOException("Error adding " + url + " to system classloader");
        }
        
    }
	
	private static final String systemClassLoaderAddUrlMethodName = "addURL";
	private static final Class<?>[] parameters = new Class[] { URL.class };
	private final Set<URL> urls;
}
