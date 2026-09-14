package com.pl.gdl.ontology.registry;

import com.pl.gdl.common.model.OntoInfoRsp;
import com.pl.gdl.ontology.annotation.Column;
import com.pl.gdl.ontology.annotation.Table;
import com.pl.gdl.ontology.model.Ontology;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

public class OntologyMetadata {
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
        } catch (Exception ignored) {}

        List<OntoInfoRsp.OntoField> fieldList = new ArrayList<>();
        for (FieldMetadata fm : fields) {
            String colName = fm.getColumnAnnotation() != null && !fm.getColumnAnnotation().cName().isEmpty()
                    ? fm.getColumnAnnotation().cName() : fm.getFieldName();
            String dt = fm.getColumnAnnotation() != null ? fm.getColumnAnnotation().dataTypeName() : "string";
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
