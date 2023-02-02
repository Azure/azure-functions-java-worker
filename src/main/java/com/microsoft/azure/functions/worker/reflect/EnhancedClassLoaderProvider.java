package com.microsoft.azure.functions.worker.reflect;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;


import com.microsoft.azure.functions.worker.*;

public class EnhancedClassLoaderProvider implements ClassLoaderProvider {

    public EnhancedClassLoaderProvider() {
        customerUrls = Collections.newSetFromMap(new ConcurrentHashMap<URL, Boolean>());
        workerUrls = Collections.newSetFromMap(new ConcurrentHashMap<URL, Boolean>());
    }

    /*
     * @see com.microsoft.azure.functions.reflect.ClassLoaderProvider#createClassLoader()
     */
    @Override
    public ClassLoader createClassLoader() {
        if (classLoaderInstance == null) {
            synchronized (lock) {
                if (classLoaderInstance == null) {
                    List<URL> urlsList = new ArrayList<>();
                    urlsList.addAll(customerUrls);
                    urlsList.addAll(workerUrls);
                    URL[] urlsForClassLoader = urlsList.toArray(new URL[0]);
                    URLClassLoader loader = new URLClassLoader(urlsForClassLoader);
                    classLoaderInstance = loader;
                }
            }
        }
        return classLoaderInstance;
    }

    @Override
    public void addCustomerUrl(URL url) throws IOException {
        if (customerUrls.contains(url)) {
            return;
        }
        WorkerLogManager.getSystemLogger().info("Loading customer file URL: " + url);
        customerUrls.add(url);
    }

    @Override
    public void addWorkerUrl(URL url) throws IOException {
        if (workerUrls.contains(url)) {
            return;
        }
        WorkerLogManager.getSystemLogger().info("Loading worker file URL: " + url);
        workerUrls.add(url);
    }

    private final Set<URL> customerUrls;
    private final Set<URL> workerUrls;
    private final Object lock = new Object();
    private volatile URLClassLoader classLoaderInstance;
}
