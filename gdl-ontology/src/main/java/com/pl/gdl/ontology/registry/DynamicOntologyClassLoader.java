package com.pl.gdl.ontology.registry;

import groovy.lang.GroovyClassLoader;
import org.codehaus.groovy.control.CompilerConfiguration;

public class DynamicOntologyClassLoader extends GroovyClassLoader {
    public DynamicOntologyClassLoader() {
        super(DynamicOntologyClassLoader.class.getClassLoader(), new CompilerConfiguration());
    }

    public DynamicOntologyClassLoader(ClassLoader parent) {
        super(parent, new CompilerConfiguration());
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        // Child-first for dynamically compiled classes
        Class<?> c = findLoadedClass(name);
        if (c != null) {
            return c;
        }
        return super.loadClass(name, resolve);
    }
}
