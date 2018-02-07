package com.microsoft.azure.webjobs.script.reflect;

import java.io.IOException;
import java.nio.file.Paths;

import org.apache.commons.lang3.StringUtils;

public class FunctionDescriptor {

	public FunctionDescriptor(String id, String name, String jarPath) {
		this.id = id;
		this.name = name;
		this.jarPath = StringUtils.trim(jarPath);
        
		GuardAgainstNullId();
		GuardAgainstUnqualifiedJarPath();
	}
	
	public String getId() { return id; }
	
	/*
	 * Gets the name of the Function
	 */
	public String getName() { return name; }
	
	public String getJarPath() { return jarPath; }
	
	private void GuardAgainstNullId() {
		if (id == null || jarPath == null) {
	        throw new NullPointerException("id cannot not be null.");
		}
	}
	
	private void GuardAgainstUnqualifiedJarPath() {
		if (jarPath == null) {
	        throw new NullPointerException("JarPath cannot not be null.");
		}
		
        if (StringUtils.isBlank(jarPath)) {
            throw new IllegalArgumentException("\"" + jarPath + "\" is not a qualified JAR file name");
        }
	}
	
	private final String id;
	private final String jarPath;
	private final String name;
}
