package com.microsoft.azure.functions.worker.description;

import java.io.*;
import java.util.*;

import org.apache.commons.lang3.*;

public class FunctionMethodDescriptor {
    private File parentDirectory;
    private File jarDirectory;
    private MethodInfo methodInfo;

    private final String id;
    private final String jarPath;
    private final String name;
    private final String fullMethodName;
    private final boolean isWarmup;

    public FunctionMethodDescriptor(String id, String name, String fullMethodName, String jarPath, boolean isWarmup) {
        this.id = id;
        this.name = name;
        this.fullMethodName = fullMethodName;
        this.methodInfo = new MethodInfo(fullMethodName);
        this.jarPath = StringUtils.trim(jarPath);
        this.isWarmup = isWarmup;
    }
    
    /**
     * gets the function method id
     */
    public String getId() { return id; }
    
    /**
     * Gets the name of the Function method
     */
    public String getName() { return name; }
    
    /**
     * Gets the method name
     */
    public String getMethodName() { return this.methodInfo.name; }
    
    /**
     * Gets the full name of the function method
     */
    public String getFullMethodName() { return fullMethodName; }
    
    /**
     * Gets the full path of the jar file as a string
     */
    public String getJarPath() { return jarPath; }
    
    public String getFullClassName() {
        return this.methodInfo.fullClassName;
    }
    
    /**
     * Gets the directory of the jar-file 
     */
    public File getJarDirectory() {
        if (jarDirectory == null) {
            this.jarDirectory = new File(jarPath).getAbsoluteFile().getParentFile();
        }
        return jarDirectory;
    }
    
    /**
     * Gets the function method's parent directory where each function method should be residing (1-level up)
     */
    public File getParentDirectory() {
        if (this.parentDirectory == null) {
            // the conventions are:
            // 1. jar in the function's method folder or
            // 2. in the function method's parent folder where all the method directories are children

            if (isJarInMethodFolder()) { 
                this.parentDirectory = getJarDirectory().getAbsoluteFile().getParentFile(); //up one level
            }
            this.parentDirectory = this.jarDirectory.getAbsoluteFile(); // otherwise convention #2
        }
        return this.parentDirectory;
    }
    
    /**
     * Gets the lib directory (which, by convention, should be located in the parent directory for every function method)
     * @return     the optional directory as File
     * @see        java.io.File
     */
    public Optional<File> getLibDirectory() {
        File[] directories = getParentDirectory().listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
                return file.isDirectory() && file.getName().endsWith("lib");
            }
        });
        if (directories.length == 0 || directories[0] == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(directories[0].getAbsoluteFile());
    }
    
    /**
     * verifies that the method is, in fact, a fully qualified method name by checking the full class name and method name
     * 
     */
    public void validateMethodInfo() {
        this.methodInfo.verifyMethodToBeExecuted();
    }
    
    /**
     * Validates that the descriptor has is a non-null fullName, id, and qualified jar path
     * 
     */
    public void validate() {
        guardAgainstNullFullName();
        guardAgainstNullId();
        guardAgainstUnqualifiedJarPath();
    }
    
    
    boolean isJarInMethodFolder() {
        File jarDir = getJarDirectory();
        
        File[] files = jarDir.listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
                return file.getName().equals("function.json") 
                        && file.getAbsolutePath().equals(jarDir.getAbsolutePath()); //folder should be the same
            }
        });
        return files.length > 0;
    }
    
    void guardAgainstNullFullName() {
        if (fullMethodName == null) {
            throw new NullPointerException("fullName cannot not be null.");
        }
    }
    
    void guardAgainstNullId() {
        if (id == null) {
            throw new NullPointerException("id cannot not be null.");
        }
    }
    
    void guardAgainstUnqualifiedJarPath() {
        if (jarPath == null) {
            throw new NullPointerException("JarPath cannot not be null.");
        }
        
        if (StringUtils.isBlank(jarPath)) {
            throw new IllegalArgumentException("\"" + jarPath + "\" is not a qualified JAR file name");
        }
    }

    public boolean isWarmup() {
        return isWarmup;
    }
    
    /*
     * "struct" to track the info on the function method
     */
    private class MethodInfo {
        public String fullClassName;
        public String fullName;
        public String name;
        
        public MethodInfo(String fullMethodName) {
            this.fullName = fullMethodName;
            this.fullClassName = StringUtils.trim(StringUtils.substringBeforeLast(fullMethodName, ClassUtils.PACKAGE_SEPARATOR));
            this.name = StringUtils.trim(StringUtils.substringAfterLast(fullMethodName, ClassUtils.PACKAGE_SEPARATOR));
        }
        
        void verifyMethodToBeExecuted() {
            if (StringUtils.isAnyBlank(fullClassName, this.name)) {
                throw new IllegalArgumentException("\"" + this.fullName + "\" is not a qualified full Java method name");
            }
        }
    }
}
