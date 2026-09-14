package com.pl.gdl.common.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class OntoInfoRsp implements Serializable {
    private String oId;
    private String oName;
    private String oDesc;
    private String oTable;
    private String oAuthor;
    private String version;
    private String className;
    private String parentClass;
    private List<OntoField> fields = new ArrayList<>();
    private List<OntoMethod> methods = new ArrayList<>();

    public OntoInfoRsp() {}

    public String getOId() { return oId; }
    public void setOId(String oId) { this.oId = oId; }

    public String getOName() { return oName; }
    public void setOName(String oName) { this.oName = oName; }

    public String getODesc() { return oDesc; }
    public void setODesc(String oDesc) { this.oDesc = oDesc; }

    public String getOTable() { return oTable; }
    public void setOTable(String oTable) { this.oTable = oTable; }

    public String getOAuthor() { return oAuthor; }
    public void setOAuthor(String oAuthor) { this.oAuthor = oAuthor; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }

    public String getParentClass() { return parentClass; }
    public void setParentClass(String parentClass) { this.parentClass = parentClass; }

    public List<OntoField> getFields() { return fields; }
    public void setFields(List<OntoField> fields) { this.fields = fields; }

    public List<OntoMethod> getMethods() { return methods; }
    public void setMethods(List<OntoMethod> methods) { this.methods = methods; }

    public static class OntoField implements Serializable {
        private String fieldName;
        private String fieldType = "java.lang.String";
        private String columnName;
        private String dataTypeName = "string";
        private String remarks = "";

        public OntoField() {}

        public OntoField(String fieldName, String columnName, String remarks) {
            this.fieldName = fieldName;
            this.columnName = columnName;
            this.remarks = remarks;
        }

        public String getFieldName() { return fieldName; }
        public void setFieldName(String fieldName) { this.fieldName = fieldName; }

        public String getFieldType() { return fieldType; }
        public void setFieldType(String fieldType) { this.fieldType = fieldType; }

        public String getColumnName() { return columnName; }
        public void setColumnName(String columnName) { this.columnName = columnName; }

        public String getDataTypeName() { return dataTypeName; }
        public void setDataTypeName(String dataTypeName) { this.dataTypeName = dataTypeName; }

        public String getRemarks() { return remarks; }
        public void setRemarks(String remarks) { this.remarks = remarks; }
    }

    public static class OntoMethod implements Serializable {
        private String methodName;
        private String methodDesc = "";
        private String returnType = "void";
        private String[] params = new String[0];

        public OntoMethod() {}

        public OntoMethod(String methodName, String methodDesc, String returnType, String[] params) {
            this.methodName = methodName;
            this.methodDesc = methodDesc;
            this.returnType = returnType;
            this.params = params != null ? params : new String[0];
        }

        public String getMethodName() { return methodName; }
        public void setMethodName(String methodName) { this.methodName = methodName; }

        public String getMethodDesc() { return methodDesc; }
        public void setMethodDesc(String methodDesc) { this.methodDesc = methodDesc; }

        public String getReturnType() { return returnType; }
        public void setReturnType(String returnType) { this.returnType = returnType; }

        public String[] getParams() { return params; }
        public void setParams(String[] params) { this.params = params; }
    }
}
