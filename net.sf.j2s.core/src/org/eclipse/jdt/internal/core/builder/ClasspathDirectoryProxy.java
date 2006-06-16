package org.eclipse.jdt.internal.core.builder;

import org.eclipse.core.resources.IContainer;

public class ClasspathDirectoryProxy {
	
	private ClasspathDirectory classpath;

	public ClasspathDirectoryProxy(ClasspathDirectory classpath) {
		super();
		this.classpath = classpath;
	}
	
	public IContainer getBinaryFolder() {
		return classpath.binaryFolder;
	}
}
