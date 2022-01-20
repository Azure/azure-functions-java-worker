package com.microsoft.azure.functions.worker.reflect;

import java.io.*;
import java.net.*;
import java.sql.Driver;
import java.util.*;
import java.util.concurrent.*;


import com.microsoft.azure.functions.worker.*;

public class EnhancedClassLoaderProvider implements ClassLoaderProvider {
    public EnhancedClassLoaderProvider() {
        urls = Collections.newSetFromMap(new ConcurrentHashMap<URL, Boolean>());
    }

    /*
     * @see com.microsoft.azure.functions.reflect.ClassLoaderProvider#createClassLoader()
     */
    @Override
    public ClassLoader createClassLoader() {
        if (classLoaderInstance == null) {
            synchronized (lock) {
                if (classLoaderInstance == null) {
                    URL[] urlsForClassLoader = new URL[urls.size()];
                    urls.toArray(urlsForClassLoader);
                    URLClassLoader loader = new URLClassLoader(urlsForClassLoader);
                    loadDrivers(loader);
                    classLoaderInstance = loader;
                }
            }
        }
        return classLoaderInstance;
    }

    private void loadDrivers(URLClassLoader classLoader) {
        Thread.currentThread().setContextClassLoader(classLoader);
        try {
            ServiceLoader<Driver> loadedDrivers = ServiceLoader.load(Driver.class);
            Iterator<Driver> driversIterator = loadedDrivers.iterator();
            try {
                while (driversIterator.hasNext()) {
                    driversIterator.next();
                }
            } catch (Throwable t) {
                // Do nothing
            }
        } finally {
            Thread.currentThread().setContextClassLoader(ClassLoader.getSystemClassLoader());
        }
    }

    @Override
    public void addUrl(URL url) throws IOException {
        if (urls.contains(url)) {
            return;
        }

        WorkerLogManager.getSystemLogger().info("Loading file URL: " + url);

        urls.add(url);
    }
    private final Set<URL> urls;
    private final Object lock = new Object();
    private static volatile URLClassLoader classLoaderInstance;
}
