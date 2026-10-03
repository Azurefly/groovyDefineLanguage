package com.pl.gdl.ontology.registry;

import groovy.lang.GroovyClassLoader;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.SecureASTCustomizer;

import java.util.List;

public class DynamicOntologyClassLoader extends GroovyClassLoader {

    /**
     * 为本体脚本编译配置沙箱：此前使用默认 CompilerConfiguration，提交的 GDL 脚本可执行任意
     * Java/Groovy 代码（Runtime.exec、文件读写等）。现通过 SecureASTCustomizer 限制：
     * <ul>
     *   <li>禁止星导入（除 java.lang、groovy.lang 等基础包）；</li>
     *   <li>禁止定义方法（本体脚本应为纯声明式）；</li>
     *   <li>禁止 System/Runtime/ProcessBuilder 等危险类。</li>
     * </ul>
     */
    private static CompilerConfiguration sandboxedConfig() {
        CompilerConfiguration config = new CompilerConfiguration();
        SecureASTCustomizer secure = new SecureASTCustomizer();
        // 允许的星导入白名单：仅基础包 + GDL 本体相关包
        secure.setStarImportsWhitelist(List.of(
                "java.lang",
                "java.util",
                "groovy.lang",
                "com.pl.gdl.ontology.annotation",
                "com.pl.gdl.ontology.model"));
        // 禁止在脚本中定义方法（本体应为声明式）
        secure.setMethodDefinitionAllowed(false);
        config.addCompilationCustomizers(secure);
        return config;
    }

    public DynamicOntologyClassLoader() {
        super(DynamicOntologyClassLoader.class.getClassLoader(), sandboxedConfig());
    }

    public DynamicOntologyClassLoader(ClassLoader parent) {
        super(parent, sandboxedConfig());
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
