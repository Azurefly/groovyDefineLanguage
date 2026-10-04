package com.pl.gdl.runtime.compiler;

import com.pl.gdl.dataframe.dataframe.CmdDataframe;
import com.pl.gdl.runtime.dag.DagGraph;
import com.pl.gdl.runtime.script.GdlExecutionContext;
import com.pl.gdl.runtime.script.GdlScriptBase;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import org.codehaus.groovy.ast.ClassCodeVisitorSupport;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.ImportNode;
import org.codehaus.groovy.ast.ModuleNode;
import org.codehaus.groovy.ast.expr.ConstructorCallExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.expr.StaticMethodCallExpression;
import org.codehaus.groovy.classgen.GeneratorContext;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.customizers.CompilationCustomizer;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.codehaus.groovy.control.customizers.SecureASTCustomizer;
import org.codehaus.groovy.control.messages.ExceptionMessage;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * GDL 脚本编译器：负责把 GDL DSL 脚本编译并执行为数据流水线。
 *
 * <p>默认工作在<b>沙箱模式</b>下（见 {@link #sandboxed()}），通过两层防护限制脚本能力：</p>
 * <ol>
 *   <li>{@link SecureASTCustomizer}：星导入白名单仅允许 {@code com.pl.gdl.**}，
 *       禁止脚本内定义方法；</li>
 *   <li>内嵌的 {@code GdlSandboxCustomizer}（CONVERSION 阶段）：在 AST 上直接拦截危险调用，
 *       包括 {@code System.exit}、{@code Runtime.exec}、<b>任意</b> {@code .execute()} 调用、
 *       {@code Class.forName}、{@code .getClass()}、{@code eval}/{@code evaluate}、
 *       文件类构造器、{@code GroovyShell}/{@code GroovyClassLoader} 以及白名单外的 import。
 *       注意 {@code .execute()} 按方法名拦截而不依赖接收者类型，因为
 *       {@code def c = 'ls'; c.execute()} 这类写法在 CONVERSION 阶段类型尚未解析，
 *       只看接收者类型会被绕过。</li>
 * </ol>
 *
 * <p>沙箱拦截在编译期以 {@link SecurityException} 抛出；Groovy 会把它包裹进
 * {@link MultipleCompilationErrorsException}，{@link #execute(String, Map)} 会将其解包后
 * 透出原始异常，调用方可直接捕获 {@link SecurityException} 识别沙箱拦截。</p>
 */
public class GdlCompiler {
    private final CompilerConfiguration configuration;
    private final ClassLoader parentClassLoader;

    /**
     * 创建沙箱模式编译器（默认）。
     */
    public GdlCompiler() {
        this(null, false);
    }

    /**
     * 使用指定父类加载器创建沙箱模式编译器（默认）。
     */
    public GdlCompiler(ClassLoader parentClassLoader) {
        this(parentClassLoader, false);
    }

    private GdlCompiler(ClassLoader parentClassLoader, boolean trusted) {
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

        if (!trusted) {
            SecureASTCustomizer secure = new SecureASTCustomizer();
            // Groovy 4.0.18 中 setStarImportsWhitelist 已废弃，使用 setAllowedStarImports
            secure.setAllowedStarImports(List.of("com.pl.gdl.**"));
            secure.setMethodDefinitionAllowed(false);
            this.configuration.addCompilationCustomizers(secure, new GdlSandboxCustomizer());
        }
    }

    /**
     * 创建沙箱模式的编译器（默认使用）。
     *
     * <p>沙箱会拦截文件 IO、进程创建、反射、动态求值等危险能力，适合执行不可信或半可信的脚本。</p>
     */
    public static GdlCompiler sandboxed() {
        return new GdlCompiler(null, false);
    }

    /**
     * 创建完全关闭沙箱的编译器。
     *
     * <p><b>警告：</b>该模式不对脚本做任何安全限制，脚本可执行任意代码
     * （如 {@code System.exit}、文件 IO、进程创建、反射、动态类加载等）。
     * 仅允许在完全受信任的环境（如内部运维控制台、可信任务编排）中使用，
     * 绝不能用于执行不可信的用户输入。</p>
     */
    public static GdlCompiler trusted() {
        return new GdlCompiler(null, true);
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
        } catch (MultipleCompilationErrorsException e) {
            // 沙箱在 CONVERSION 阶段抛出的 SecurityException 会被 Groovy 包裹，
            // 在这里解包并透出原始异常
            throw unwrapSandboxViolation(e);
        } finally {
            GdlExecutionContext.clear();
        }
    }

    /**
     * CONVERSION 阶段抛出的 {@link SecurityException} 会被 Groovy 包裹进
     * {@link MultipleCompilationErrorsException}（以 {@link ExceptionMessage} 形式
     * 存放在 ErrorCollector 中），此处将其解包并透出原始异常，便于调用方识别沙箱拦截；
     * 若不包含沙箱拦截，则原样抛出编译异常。
     */
    private static RuntimeException unwrapSandboxViolation(MultipleCompilationErrorsException e) {
        for (Object error : e.getErrorCollector().getErrors()) {
            if (error instanceof ExceptionMessage message && message.getCause() instanceof SecurityException securityException) {
                return securityException;
            }
        }
        return e;
    }

    /**
     * 纯规划：将脚本解析为 DAG，不产生任何副作用。
     * <p>脚本仍会被"执行"一次以构建算子链，但全程处于 dry-run 模式：
     * 所有 ExecutionEngine 不触碰数据源、writeCsv/writeJson 不落地文件，
     * 因此 collect/DDL/写回等终端动作都不会真实发生。算子构造与 DAG
     * 节点注册不受影响。
     */
    public DagGraph parseToDag(String scriptText, Map<String, Object> params) {
        GdlExecutionContext context = new GdlExecutionContext();
        if (params != null) {
            context.setScriptParameters(params);
        }
        GdlExecutionContext.set(context);
        try {
            try {
                // dry-run 下执行脚本以构建完整 DAG；DSL 语义错误仍会抛出
                com.pl.gdl.dataframe.engine.DryRun.run(() ->
                        executeWithContext(context, scriptText, params));
            } catch (Exception e) {
                // 仅编译期错误才抛出；其他异常（如 DSL 用法错误）不影响已构建的 DAG 节点
                // 注意：dry-run 下不应再出现"表不存在"类数据异常
                if (isCompilationError(e)) {
                    throw e;
                }
            }
            return context.getDagGraph();
        } finally {
            GdlExecutionContext.clear();
        }
    }

    private void executeWithContext(GdlExecutionContext context, String scriptText, Map<String, Object> params) {
        Binding binding = new Binding();
        if (params != null) {
            params.forEach(binding::setVariable);
        }
        GroovyShell shell = new GroovyShell(parentClassLoader, binding, configuration);
        Script script = shell.parse(scriptText);
        script.run();
    }

    private boolean isCompilationError(Exception e) {
        return e instanceof MultipleCompilationErrorsException
                || e instanceof org.codehaus.groovy.control.CompilationFailedException;
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

    /**
     * GDL 沙箱的 AST 拦截器，运行在 CONVERSION 阶段。
     *
     * <p>在该阶段类型的解析尚未完成，因此所有危险调用都按<b>方法名 / 类型名</b>做保守拦截，
     * 而不依赖接收者类型推断，避免 {@code def c = 'ls'; c.execute()} 这类变量接收者绕过。</p>
     */
    static final class GdlSandboxCustomizer extends CompilationCustomizer {

        /** 禁止直接 new 的类型（短名与全限定名都会被拦截）。 */
        private static final Set<String> DENIED_CONSTRUCTORS = new HashSet<>(Arrays.asList(
                "File", "java.io.File",
                "FileInputStream", "java.io.FileInputStream",
                "FileOutputStream", "java.io.FileOutputStream",
                "FileReader", "java.io.FileReader",
                "FileWriter", "java.io.FileWriter",
                "RandomAccessFile", "java.io.RandomAccessFile",
                "ProcessBuilder", "java.lang.ProcessBuilder",
                "Path", "java.nio.file.Path",
                "Paths", "java.nio.file.Paths",
                "Files", "java.nio.file.Files",
                "GroovyShell", "groovy.lang.GroovyShell",
                "GroovyClassLoader", "groovy.lang.GroovyClassLoader"
        ));

        /** 禁止显式 import / 静态 import 的类型。 */
        private static final Set<String> DENIED_IMPORTS = new HashSet<>(Arrays.asList(
                "java.lang.System",
                "java.lang.Runtime",
                "java.lang.ProcessBuilder",
                "java.lang.ClassLoader",
                "java.io.File",
                "java.io.FileInputStream",
                "java.io.FileOutputStream",
                "java.io.FileReader",
                "java.io.FileWriter",
                "java.io.RandomAccessFile",
                "java.nio.file.Files",
                "java.nio.file.Path",
                "java.nio.file.Paths",
                "groovy.lang.GroovyShell",
                "groovy.lang.GroovyClassLoader",
                "groovy.util.Eval"
        ));

        GdlSandboxCustomizer() {
            super(CompilePhase.CONVERSION);
        }

        @Override
        public void call(SourceUnit source, GeneratorContext context, ClassNode classNode) {
            ModuleNode module = source.getAST();
            if (module == null || classNode == null) {
                return;
            }
            checkImports(module);
            new SandboxVisitor(source).visitClass(classNode);
        }

        private void checkImports(ModuleNode module) {
            for (ImportNode starImport : module.getStarImports()) {
                String pkg = starImport.getPackageName();
                if (!isWhitelistedStarImport(pkg)) {
                    deny("星导入 " + pkg + "* 不在沙箱白名单内，仅允许 com.pl.gdl.**");
                }
            }
            for (ImportNode importNode : module.getImports()) {
                String className = importNode.getClassName();
                if (className != null && DENIED_IMPORTS.contains(className)) {
                    deny("导入 " + className + " 被沙箱禁止");
                }
            }
            for (ImportNode staticImport : module.getStaticImports().values()) {
                String className = staticImport.getClassName();
                if (className != null && DENIED_IMPORTS.contains(className)) {
                    deny("静态导入 " + className + " 被沙箱禁止");
                }
            }
            for (ImportNode staticStarImport : module.getStaticStarImports().values()) {
                String className = staticStarImport.getClassName();
                if (className != null && (DENIED_IMPORTS.contains(className) || !className.startsWith("com.pl.gdl."))) {
                    deny("静态星导入 " + className + ".* 被沙箱禁止");
                }
            }
        }

        private static boolean isWhitelistedStarImport(String packageName) {
            if (packageName == null) {
                return false;
            }
            String pkg = packageName.endsWith(".")
                    ? packageName.substring(0, packageName.length() - 1)
                    : packageName;
            return "com.pl.gdl".equals(pkg) || pkg.startsWith("com.pl.gdl.");
        }

        private static void deny(String message) {
            throw new SecurityException("[GDL 沙箱] " + message);
        }

        /**
         * 遍历脚本 AST 并拦截危险调用的访问器。
         */
        private static final class SandboxVisitor extends ClassCodeVisitorSupport {
            private final SourceUnit sourceUnit;

            SandboxVisitor(SourceUnit sourceUnit) {
                this.sourceUnit = sourceUnit;
            }

            @Override
            protected SourceUnit getSourceUnit() {
                return sourceUnit;
            }

            @Override
            public void visitMethodCallExpression(MethodCallExpression call) {
                String method = call.getMethodAsString();
                String receiver = textOf(call.getObjectExpression());
                if ("execute".equals(method)) {
                    // 按方法名拦截任意接收者的 execute()：CONVERSION 阶段类型未解析，
                    // def c = 'ls'; c.execute() 这类变量接收者无法通过类型检查发现
                    deny("." + method + "() 调用被沙箱禁止（任意接收者）");
                } else if ("getClass".equals(method)) {
                    deny(".getClass() 调用被沙箱禁止");
                } else if ("eval".equals(method) || "evaluate".equals(method)) {
                    deny("." + method + "() 动态求值调用被沙箱禁止");
                } else if ("exit".equals(method) && isSystemReceiver(receiver)) {
                    deny("System.exit() 调用被沙箱禁止");
                } else if ("exec".equals(method) && receiver != null && receiver.contains("Runtime")) {
                    deny("Runtime.exec() 调用被沙箱禁止");
                } else if ("forName".equals(method) && isClassReceiver(receiver)) {
                    deny("Class.forName() 调用被沙箱禁止");
                }
                super.visitMethodCallExpression(call);
            }

            @Override
            public void visitStaticMethodCallExpression(StaticMethodCallExpression call) {
                String owner = call.getOwnerType().getName();
                String method = call.getMethod();
                if ("exit".equals(method) && ("System".equals(owner) || "java.lang.System".equals(owner))) {
                    deny("System.exit() 调用被沙箱禁止");
                } else if ("exec".equals(method) && ("Runtime".equals(owner) || "java.lang.Runtime".equals(owner))) {
                    deny("Runtime.exec() 调用被沙箱禁止");
                } else if ("forName".equals(method) && ("Class".equals(owner) || "java.lang.Class".equals(owner))) {
                    deny("Class.forName() 调用被沙箱禁止");
                }
                super.visitStaticMethodCallExpression(call);
            }

            @Override
            public void visitConstructorCallExpression(ConstructorCallExpression call) {
                String typeName = call.getType().getName();
                if (DENIED_CONSTRUCTORS.contains(typeName)) {
                    deny("new " + typeName + "(...) 被沙箱禁止");
                }
                super.visitConstructorCallExpression(call);
            }

            private static String textOf(Expression expression) {
                try {
                    return expression == null ? null : expression.getText();
                } catch (Exception e) {
                    return null;
                }
            }

            private static boolean isSystemReceiver(String receiver) {
                return "System".equals(receiver)
                        || "java.lang.System".equals(receiver)
                        || (receiver != null && receiver.endsWith(".System"));
            }

            private static boolean isClassReceiver(String receiver) {
                return "Class".equals(receiver)
                        || "java.lang.Class".equals(receiver)
                        || (receiver != null && receiver.endsWith(".Class"));
            }
        }
    }
}
