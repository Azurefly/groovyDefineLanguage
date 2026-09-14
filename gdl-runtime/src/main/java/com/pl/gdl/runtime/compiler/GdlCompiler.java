package com.pl.gdl.runtime.compiler;

import com.pl.gdl.dataframe.dataframe.CmdDataframe;
import com.pl.gdl.runtime.dag.DagGraph;
import com.pl.gdl.runtime.script.GdlExecutionContext;
import com.pl.gdl.runtime.script.GdlScriptBase;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;

import java.util.Map;

public class GdlCompiler {
    private final CompilerConfiguration configuration;
    private final ClassLoader parentClassLoader;

    public GdlCompiler() {
        this(GdlCompiler.class.getClassLoader());
    }

    public GdlCompiler(ClassLoader parentClassLoader) {
        this.parentClassLoader = parentClassLoader != null ? parentClassLoader : GdlCompiler.class.getClassLoader();
        this.configuration = new CompilerConfiguration();
        this.configuration.setScriptBaseClass(GdlScriptBase.class.getName());

        ImportCustomizer importCustomizer = new ImportCustomizer();
        importCustomizer.addStarImports(
                "com.pl.gdl.common.model",
                "com.pl.gdl.dataframe.dataframe",
                "com.pl.gdl.dataframe.datasource"
        );
        this.configuration.addCompilationCustomizers(importCustomizer);
    }

    public CompilerConfiguration getConfiguration() {
        return configuration;
    }

    public GdlExecutionResult execute(String scriptText, Map<String, Object> params) {
        GdlExecutionContext context = new GdlExecutionContext();
        if (params != null) {
            context.setScriptParameters(params);
        }
        GdlExecutionContext.set(context);

        try {
            Binding binding = new Binding();
            if (params != null) {
                params.forEach(binding::setVariable);
            }

            GroovyShell shell = new GroovyShell(parentClassLoader, binding, configuration);
            Script script = shell.parse(scriptText);
            Object evalResult = script.run();

            CmdDataframe returnDf = context.getReturnDf();
            return new GdlExecutionResult(context, evalResult, returnDf);
        } finally {
            GdlExecutionContext.clear();
        }
    }

    public DagGraph parseToDag(String scriptText, Map<String, Object> params) {
        GdlExecutionResult res = execute(scriptText, params);
        return res.getContext().getDagGraph();
    }

    public static class GdlExecutionResult {
        private final GdlExecutionContext context;
        private final Object scriptResult;
        private final CmdDataframe returnDf;

        public GdlExecutionResult(GdlExecutionContext context, Object scriptResult, CmdDataframe returnDf) {
            this.context = context;
            this.scriptResult = scriptResult;
            this.returnDf = returnDf;
        }

        public GdlExecutionContext getContext() { return context; }
        public Object getScriptResult() { return scriptResult; }
        public CmdDataframe getReturnDf() { return returnDf; }
    }
}
