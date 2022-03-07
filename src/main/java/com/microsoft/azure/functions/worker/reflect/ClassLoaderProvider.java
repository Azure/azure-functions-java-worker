package com.microsoft.azure.functions.worker.reflect;

import java.io.*;
import java.net.*;

public interface ClassLoaderProvider {
    
    /*
     * Adds a customer search path to be used by the provided class loader
     */
    void addCustomerUrl(URL url) throws IOException;

    /*
     * Adds a worker search path to be used by the provided class loader
     */
    void addWorkerUrl(URL url) throws IOException;

    /*
     * Create the class loader with the required search paths
     */
    ClassLoader createClassLoader();
}
