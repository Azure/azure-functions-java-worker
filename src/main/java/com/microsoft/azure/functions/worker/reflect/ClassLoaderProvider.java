package com.microsoft.azure.functions.worker.reflect;

import java.io.*;
import java.net.*;

public interface ClassLoaderProvider {
    
    /*
     * Adds a search path to be used by the provided class loader
     */
    void addUrl(URL url) throws IOException;
    
    /*
     * Adds all jar-files found in a directory (NOT recursive)
     */
    void addDirectory(File directory) throws MalformedURLException, IOException;
    
    /*
     * Gets the class loader with the required search paths
     */
    ClassLoader getClassLoader();
}
