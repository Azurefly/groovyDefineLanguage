package com.pl.gdl.ontology.registry;

import com.pl.gdl.common.exception.OntologyValidationException;
import com.pl.gdl.common.model.OntoInfoRsp;
import com.pl.gdl.common.model.RegisterRsp;
import com.pl.gdl.ontology.model.Ontology;
import groovy.lang.GroovySystem;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本体注册中心（单例）：负责 GDL 本体脚本的编译、注册、更新、注销与查询。
 *
 * <p>注册语义：</p>
 * <ul>
 *   <li>脚本编译失败、类未继承 {@link Ontology}、重复注册等情况返回
 *       {@link RegisterRsp#fail(String)}；</li>
 *   <li>注册前会实例化本体并调用 {@link Ontology#validate()} 做校验，
 *       <b>校验失败直接抛 {@link OntologyValidationException}（不再转为 RegisterRsp）</b>，
 *       便于上层（如 HTTP 服务）映射为精确的错误状态码；</li>
 *   <li>{@link #getOntologies(String, String)} 支持按地域编码过滤：
 *       areaCode 为空不过滤，本体未声明地域视作全局可用，比较时忽略大小写。</li>
 * </ul>
 */
public class OntologyRegistry {
    private static final OntologyRegistry INSTANCE = new OntologyRegistry();

    private final Map<String, OntologyMetadata> registeredOntologies = new ConcurrentHashMap<>();
    private DynamicOntologyClassLoader classLoader = new DynamicOntologyClassLoader();

    private OntologyRegistry() {}

    public static OntologyRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * 注册单个本体脚本。
     *
     * @throws OntologyValidationException 本体实例化成功但 {@link Ontology#validate()} 校验失败时抛出
     */
    public synchronized RegisterRsp registerOntology(String gdlScript) {
        if (gdlScript == null || gdlScript.isBlank()) {
            return RegisterRsp.fail("GDL script content cannot be empty");
        }
        final Class<? extends Ontology> ontoClass;
        try {
            Class<?> clazz = classLoader.parseClass(gdlScript);
            if (!Ontology.class.isAssignableFrom(clazz)) {
                return RegisterRsp.fail("Class does not extend " + Ontology.class.getName());
            }
            @SuppressWarnings("unchecked")
            Class<? extends Ontology> casted = (Class<? extends Ontology>) clazz;
            ontoClass = casted;
        } catch (Exception e) {
            return RegisterRsp.fail("Failed to compile ontology: " + e.getMessage());
        }

        String fullName = ontoClass.getName();
        if (registeredOntologies.containsKey(fullName)) {
            return RegisterRsp.fail("Ontology " + fullName + " is already registered, please rename or update");
        }

        final Ontology instance;
        try {
            instance = ontoClass.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            return RegisterRsp.fail("Failed to instantiate ontology " + fullName + ": " + e.getMessage());
        }
        // 校验失败直接抛 OntologyValidationException，不再转为 RegisterRsp
        instance.validate();

        OntologyMetadata meta = new OntologyMetadata(ontoClass);
        registeredOntologies.put(fullName, meta);
        return RegisterRsp.success("Ontology registered successfully", Map.of(fullName, "SUCCESS"));
    }

    public synchronized RegisterRsp registerOntologies(List<String> gdlScripts) {
        if (gdlScripts == null || gdlScripts.isEmpty()) {
            return RegisterRsp.fail("Empty script list");
        }
        Map<String, String> results = new LinkedHashMap<>();
        int successCount = 0;

        for (String script : gdlScripts) {
            try {
                RegisterRsp rsp = registerOntology(script);
                if (rsp.getStatus() == RegisterRsp.STATUS_SUCCESS) {
                    successCount++;
                    if (rsp.getData() instanceof Map<?, ?> m) {
                        m.forEach((k, v) -> results.put(String.valueOf(k), String.valueOf(v)));
                    }
                } else {
                    results.put("script_" + results.size(), rsp.getMessage());
                }
            } catch (OntologyValidationException e) {
                // 批量注册时单个本体校验失败不中断整体流程，仅记录失败原因
                results.put("script_" + results.size(), e.getMessage());
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

    /**
     * 查询已注册本体。
     *
     * @param fullOntologyNames 本体全限定名（逗号分隔），为空则返回全部
     * @param areaCode 地域编码过滤条件；为空或 blank 时不过滤。
     *                 本体未声明地域视作全局可用；地域比较忽略大小写
     */
    public List<OntoInfoRsp> getOntologies(String fullOntologyNames, String areaCode) {
        boolean filterByArea = areaCode != null && !areaCode.isBlank();
        String wantedArea = filterByArea ? areaCode.trim() : null;

        List<OntologyMetadata> candidates = new ArrayList<>();
        if (fullOntologyNames == null || fullOntologyNames.isBlank()) {
            candidates.addAll(registeredOntologies.values());
        } else {
            for (String name : fullOntologyNames.split(",")) {
                String trimmed = name.trim();
                OntologyMetadata meta = registeredOntologies.get(trimmed);
                if (meta != null) {
                    candidates.add(meta);
                }
            }
        }

        List<OntoInfoRsp> list = new ArrayList<>();
        for (OntologyMetadata meta : candidates) {
            if (filterByArea && !isAvailableInArea(meta, wantedArea)) {
                continue;
            }
            list.add(meta.toOntoInfoRsp());
        }
        return list;
    }

    /**
     * 地域可用性判断：本体未声明地域（areaCodes 为空）视作全局可用；忽略大小写比较。
     */
    private boolean isAvailableInArea(OntologyMetadata meta, String wantedArea) {
        List<String> areas = meta.getAreaCodes();
        if (areas == null || areas.isEmpty()) {
            return true;
        }
        for (String area : areas) {
            if (area != null && area.equalsIgnoreCase(wantedArea)) {
                return true;
            }
        }
        return false;
    }

    public DynamicOntologyClassLoader getClassLoader() {
        return classLoader;
    }
}
