package com.pl.gdl.ontology.registry;

import com.pl.gdl.common.model.OntoInfoRsp;
import com.pl.gdl.common.model.RegisterRsp;
import com.pl.gdl.ontology.model.Ontology;
import groovy.lang.GroovySystem;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class OntologyRegistry {
    private static final OntologyRegistry INSTANCE = new OntologyRegistry();

    private final Map<String, OntologyMetadata> registeredOntologies = new ConcurrentHashMap<>();
    private DynamicOntologyClassLoader classLoader = new DynamicOntologyClassLoader();

    private OntologyRegistry() {}

    public static OntologyRegistry getInstance() {
        return INSTANCE;
    }

    public synchronized RegisterRsp registerOntology(String gdlScript) {
        if (gdlScript == null || gdlScript.isBlank()) {
            return RegisterRsp.fail("GDL script content cannot be empty");
        }
        try {
            Class<?> clazz = classLoader.parseClass(gdlScript);
            if (!Ontology.class.isAssignableFrom(clazz)) {
                return RegisterRsp.fail("Class does not extend " + Ontology.class.getName());
            }
            @SuppressWarnings("unchecked")
            Class<? extends Ontology> ontoClass = (Class<? extends Ontology>) clazz;
            String fullName = ontoClass.getName();

            if (registeredOntologies.containsKey(fullName)) {
                return RegisterRsp.fail("Ontology " + fullName + " is already registered, please rename or update");
            }

            OntologyMetadata meta = new OntologyMetadata(ontoClass);
            registeredOntologies.put(fullName, meta);
            return RegisterRsp.success("Ontology registered successfully", Map.of(fullName, "SUCCESS"));
        } catch (Exception e) {
            return RegisterRsp.fail("Failed to compile ontology: " + e.getMessage());
        }
    }

    public synchronized RegisterRsp registerOntologies(List<String> gdlScripts) {
        if (gdlScripts == null || gdlScripts.isEmpty()) {
            return RegisterRsp.fail("Empty script list");
        }
        Map<String, String> results = new LinkedHashMap<>();
        int successCount = 0;

        for (String script : gdlScripts) {
            RegisterRsp rsp = registerOntology(script);
            if (rsp.getStatus() == RegisterRsp.STATUS_SUCCESS) {
                successCount++;
                if (rsp.getData() instanceof Map<?, ?> m) {
                    m.forEach((k, v) -> results.put(String.valueOf(k), String.valueOf(v)));
                }
            } else {
                results.put("script_" + results.size(), rsp.getMessage());
            }
        }

        if (successCount == gdlScripts.size()) {
            return RegisterRsp.success("All ontologies registered", results);
        } else if (successCount > 0) {
            return RegisterRsp.partial("Partially registered", results);
        } else {
            return RegisterRsp.fail("Failed to register ontologies");
        }
    }

    public synchronized RegisterRsp updateOntologies(List<String> gdlScripts) {
        if (gdlScripts == null || gdlScripts.isEmpty()) {
            return RegisterRsp.fail("Empty script list");
        }
        Map<String, String> results = new LinkedHashMap<>();
        for (String script : gdlScripts) {
            try {
                Class<?> clazz = classLoader.parseClass(script);
                if (Ontology.class.isAssignableFrom(clazz)) {
                    @SuppressWarnings("unchecked")
                    Class<? extends Ontology> ontoClass = (Class<? extends Ontology>) clazz;
                    String fullName = ontoClass.getName();

                    // Evict old metaclass to prevent metaspace leak
                    if (registeredOntologies.containsKey(fullName)) {
                        GroovySystem.getMetaClassRegistry().removeMetaClass(registeredOntologies.get(fullName).getOntologyClass());
                    }

                    OntologyMetadata meta = new OntologyMetadata(ontoClass);
                    registeredOntologies.put(fullName, meta);
                    results.put(fullName, "UPDATED");
                }
            } catch (Exception e) {
                results.put("script_err", e.getMessage());
            }
        }
        return RegisterRsp.success("Ontologies updated", results);
    }

    public synchronized RegisterRsp unregisterOntology(String names) {
        if (names == null || names.isBlank()) {
            return RegisterRsp.fail("No ontology names specified");
        }
        String[] split = names.split(",");
        Map<String, String> results = new LinkedHashMap<>();
        for (String name : split) {
            String trimmed = name.trim();
            OntologyMetadata meta = registeredOntologies.remove(trimmed);
            if (meta != null) {
                GroovySystem.getMetaClassRegistry().removeMetaClass(meta.getOntologyClass());
                results.put(trimmed, "UNREGISTERED");
            } else {
                results.put(trimmed, "NOT_FOUND");
            }
        }
        return RegisterRsp.success("Unregister completed", results);
    }

    public List<OntoInfoRsp> getOntologies(String fullOntologyNames, String areaCode) {
        List<OntoInfoRsp> list = new ArrayList<>();
        if (fullOntologyNames == null || fullOntologyNames.isBlank()) {
            for (OntologyMetadata meta : registeredOntologies.values()) {
                list.add(meta.toOntoInfoRsp());
            }
        } else {
            for (String name : fullOntologyNames.split(",")) {
                String trimmed = name.trim();
                OntologyMetadata meta = registeredOntologies.get(trimmed);
                if (meta != null) {
                    list.add(meta.toOntoInfoRsp());
                }
            }
        }
        return list;
    }

    public DynamicOntologyClassLoader getClassLoader() {
        return classLoader;
    }
}
