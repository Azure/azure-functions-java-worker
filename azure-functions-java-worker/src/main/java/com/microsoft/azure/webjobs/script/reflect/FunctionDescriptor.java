package com.microsoft.azure.webjobs.script.reflect;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Optional;

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
	
	public File getJarDirectory() {
		if (jarDirectory == null) {
			this.jarDirectory = new File(jarPath).getAbsoluteFile().getParentFile();
		}
		return jarDirectory;
	}
	
	/*
	 * Gets the function's directory where each function method resides
	 */
	public File getDirectory() {
		if (this.functionDirectory == null) {
			// the conventions are:
			// 1. jar in the function's method folder or
			// 2. in the function's folder where all the method directories are children

			if (isJarInMethodFolder()) { 
				this.functionDirectory = getJarDirectory().getAbsoluteFile().getParentFile(); //up one level
			}
			this.functionDirectory = this.jarDirectory.getAbsoluteFile(); // otherwise convention #2
		}
		return this.functionDirectory;
	}
	
	public Optional<File> getLibDirectory() {
		File[] directories = getDirectory().listFiles(new FileFilter() {
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
	
	private boolean isJarInMethodFolder() {
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
	
	private File functionDirectory;
	private File jarDirectory;
	
	private final String id;
	private final String jarPath;
	private final String name;
}
