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
        workerAnnotationLibUrls = Collections.newSetFromMap(new ConcurrentHashMap<URL, Boolean>());
    }

    /*
     * @see com.microsoft.azure.functions.reflect.ClassLoaderProvider#createClassLoader()
     */
    @Override
    public ClassLoader createClassLoader() {
        if(Helper.isCustomURLClassLoader()) {
            return getURLClassLoaderInstance();
        }else {
            return createURLClassLoaderInstance();
        }
    }

    /**
     * Create and return a singleton URL classloader
     * @return instance of URLClassLoader
     */
    private URLClassLoader getURLClassLoaderInstance() {
        if (classLoaderInstance == null) {
            synchronized (lock) {
                if (classLoaderInstance == null) {
                    classLoaderInstance = createURLClassLoaderInstance();
                }
            }
        }
        return classLoaderInstance;
    }

    private URLClassLoader createURLClassLoaderInstance(){
        List<URL> urlsList = new ArrayList<>();
        urlsList.addAll(urls);
        urlsList.addAll(workerAnnotationLibUrls);
        URL[] urlsForClassLoader = urlsList.toArray(new URL[0]);
        URLClassLoader loader = new URLClassLoader(urlsForClassLoader);
        loadDrivers(loader);
        return loader;
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
    public void addNonAnnotationLibsUrl(URL url) throws IOException {
        if (urls.contains(url)) {
            return;
        }
        WorkerLogManager.getSystemLogger().info("Loading non java annotation library file URL: " + url);
        urls.add(url);
    }

    @Override
    public void addWorkerAnnotationLibUrl(URL url) throws IOException {
        if (workerAnnotationLibUrls.contains(url)) {
            return;
        }
        WorkerLogManager.getSystemLogger().info("Loading java annotation library file URL: " + url);
        workerAnnotationLibUrls.add(url);
    }

    private final Set<URL> urls;
    private final Set<URL> workerAnnotationLibUrls;
    private final Object lock = new Object();
    private static volatile URLClassLoader classLoaderInstance;
}
