package com.pl.gdl.ontology.registry;

import com.pl.gdl.common.model.OntoInfoRsp;
import com.pl.gdl.ontology.annotation.Column;
import com.pl.gdl.ontology.annotation.Table;
import com.pl.gdl.ontology.model.Ontology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * 本体元数据：从 {@link Ontology} 子类的注解、字段与方法中抽取表结构与接口信息。
 */
public class OntologyMetadata {
    private static final Logger log = LoggerFactory.getLogger(OntologyMetadata.class);

    private final Class<? extends Ontology> ontologyClass;
    private final Table tableAnnotation;
    private final List<FieldMetadata> fields = new ArrayList<>();
    private final List<MethodMetadata> methods = new ArrayList<>();

    public OntologyMetadata(Class<? extends Ontology> ontologyClass) {
        this.ontologyClass = ontologyClass;
        this.tableAnnotation = ontologyClass.getAnnotation(Table.class);
        parseFields();
        parseMethods();
    }

    private void parseFields() {
        for (Field f : ontologyClass.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            Column col = f.getAnnotation(Column.class);
            FieldMetadata fm = new FieldMetadata(f.getName(), f.getType().getName(), col);
            fields.add(fm);
        }
    }

    private void parseMethods() {
        for (Method m : ontologyClass.getDeclaredMethods()) {
            if (Modifier.isStatic(m.getModifiers()) || !Modifier.isPublic(m.getModifiers())) continue;
            if (m.getName().startsWith("get") || m.getName().startsWith("set") || m.getName().equals("invokeMethod")) continue;
            MethodMetadata mm = new MethodMetadata(m.getName(), m.getReturnType().getSimpleName(), m.getParameterCount());
            methods.add(mm);
        }
    }

    public Class<? extends Ontology> getOntologyClass() { return ontologyClass; }
    public Table getTableAnnotation() { return tableAnnotation; }
    public List<FieldMetadata> getFields() { return fields; }
    public List<MethodMetadata> getMethods() { return methods; }

    /**
     * 返回该本体声明的可用地域编码（{@link Ontology#areaCodes}）。
     *
     * <p>未声明地域时返回空列表，调用方应将其视作全局可用；
     * 实例化失败时打 warn 日志并返回空列表。</p>
     */
    public List<String> getAreaCodes() {
        try {
            Ontology instance = ontologyClass.getDeclaredConstructor().newInstance();
            return instance.areaCodes == null ? List.of() : new ArrayList<>(instance.areaCodes);
        } catch (Exception e) {
            log.warn("无法实例化本体类 {} 以读取地域声明: {}", ontologyClass.getName(), e.toString());
            return List.of();
        }
    }

    public OntoInfoRsp toOntoInfoRsp() {
        OntoInfoRsp rsp = new OntoInfoRsp();
        rsp.setClassName(ontologyClass.getSimpleName());
        Package pkg = ontologyClass.getPackage();
        rsp.setVersion(pkg != null ? pkg.getName() : "v1");

        try {
            Ontology instance = ontologyClass.getDeclaredConstructor().newInstance();
            rsp.setOId(instance.oId);
            rsp.setOName(instance.oName);
            rsp.setODesc(instance.oDesc);
            rsp.setOTable(instance.oTable);
            rsp.setOAuthor(instance.oAuthor);
        } catch (Exception e) {
            log.warn("无法实例化本体类 {} 以抽取实例元数据: {}", ontologyClass.getName(), e.toString());
        }

        List<OntoInfoRsp.OntoField> fieldList = new ArrayList<>();
        for (FieldMetadata fm : fields) {
            String colName = fm.getColumnAnnotation() != null && !fm.getColumnAnnotation().cName().isEmpty()
                    ? fm.getColumnAnnotation().cName() : fm.getFieldName();
            String remarks = fm.getColumnAnnotation() != null ? fm.getColumnAnnotation().remarks() : "";
            fieldList.add(new OntoInfoRsp.OntoField(fm.getFieldName(), colName, remarks));
        }
        rsp.setFields(fieldList);

        List<OntoInfoRsp.OntoMethod> methodList = new ArrayList<>();
        for (MethodMetadata mm : methods) {
            methodList.add(new OntoInfoRsp.OntoMethod(mm.getName(), "", mm.getReturnType(), new String[0]));
        }
        rsp.setMethods(methodList);
        return rsp;
    }

    public static class FieldMetadata {
        private final String fieldName;
        private final String fieldType;
        private final Column columnAnnotation;

        public FieldMetadata(String fieldName, String fieldType, Column columnAnnotation) {
            this.fieldName = fieldName;
            this.fieldType = fieldType;
            this.columnAnnotation = columnAnnotation;
        }

        public String getFieldName() { return fieldName; }
        public String getFieldType() { return fieldType; }
        public Column getColumnAnnotation() { return columnAnnotation; }
    }

    public static class MethodMetadata {
        private final String name;
        private final String returnType;
        private final int paramCount;

        public MethodMetadata(String name, String returnType, int paramCount) {
            this.name = name;
            this.returnType = returnType;
            this.paramCount = paramCount;
        }

        public String getName() { return name; }
        public String getReturnType() { return returnType; }
        public int getParamCount() { return paramCount; }
    }
}
