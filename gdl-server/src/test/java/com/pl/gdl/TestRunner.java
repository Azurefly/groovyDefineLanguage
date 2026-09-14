package com.pl.gdl;

import com.pl.gdl.common.model.RowDataFrameTest;
import com.pl.gdl.dataframe.CmdDataframeTest;
import com.pl.gdl.drift.DriftTest;
import com.pl.gdl.ontology.OntologyTest;
import com.pl.gdl.runtime.GdlCompilerTest;
import com.pl.gdl.server.TreClientTest;

import java.lang.reflect.Method;
import java.util.List;

public class TestRunner {
    public static void main(String[] args) {
        List<Class<?>> testClasses = List.of(
                RowDataFrameTest.class,
                CmdDataframeTest.class,
                GdlCompilerTest.class,
                OntologyTest.class,
                DriftTest.class,
                TreClientTest.class
        );

        int passed = 0;
        int failed = 0;

        System.out.println("=================================================");
        System.out.println("   Running GDL Engine Test Suite (6 Modules)     ");
        System.out.println("=================================================");

        for (Class<?> clazz : testClasses) {
            System.out.println("Running test class: " + clazz.getSimpleName());
            try {
                Object instance = clazz.getDeclaredConstructor().newInstance();
                for (Method m : clazz.getDeclaredMethods()) {
                    if (m.isAnnotationPresent(org.junit.jupiter.api.Test.class) || m.getName().startsWith("test")) {
                        try {
                            m.invoke(instance);
                            System.out.println("  [PASS] " + m.getName());
                            passed++;
                        } catch (Throwable t) {
                            System.err.println("  [FAIL] " + m.getName() + " -> " + (t.getCause() != null ? t.getCause().getMessage() : t.getMessage()));
                            if (t.getCause() != null) {
                                t.getCause().printStackTrace();
                            } else {
                                t.printStackTrace();
                            }
                            failed++;
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Failed to instantiate " + clazz.getName() + ": " + e.getMessage());
                failed++;
            }
        }

        System.out.println("=================================================");
        System.out.println("Test Results: " + passed + " passed, " + failed + " failed.");
        System.out.println("=================================================");

        if (failed > 0) {
            System.exit(1);
        }
    }
}
