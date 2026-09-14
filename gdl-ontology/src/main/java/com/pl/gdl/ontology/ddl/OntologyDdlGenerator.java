package com.pl.gdl.ontology.ddl;

import com.pl.gdl.ontology.annotation.Column;
import com.pl.gdl.ontology.annotation.Table;
import com.pl.gdl.ontology.model.Ontology;
import com.pl.gdl.ontology.registry.OntologyMetadata;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

public class OntologyDdlGenerator {

    public static String generateHiveDdl(OntologyMetadata metadata) {
        Table table = metadata.getTableAnnotation();
        String type = table != null ? table.type() : "ORC";
        String remarks = table != null ? table.remarks() : "";

        String tableName = metadata.getOntologyClass().getSimpleName().toLowerCase();

        StringJoiner colsSj = new StringJoiner(",\n  ");
        List<String> partitions = new ArrayList<>();

        for (OntologyMetadata.FieldMetadata fm : metadata.getFields()) {
            Column col = fm.getColumnAnnotation();
            String colName = col != null && !col.cName().isEmpty() ? col.cName() : fm.getFieldName();
            String dt = col != null ? col.dataTypeName().toUpperCase() : "STRING";
            String colRemarks = col != null && !col.remarks().isEmpty() ? " COMMENT '" + col.remarks() + "'" : "";

            if (col != null && col.partition()) {
                partitions.add(colName + " " + dt + colRemarks);
            } else {
                colsSj.add(colName + " " + dt + colRemarks);
            }
        }

        StringBuilder ddl = new StringBuilder();
        ddl.append("CREATE TABLE IF NOT EXISTS ").append(tableName).append(" (\n  ")
           .append(colsSj)
           .append("\n)");

        if (!remarks.isEmpty()) {
            ddl.append(" COMMENT '").append(remarks).append("'");
        }

        if (!partitions.isEmpty()) {
            ddl.append(" PARTITIONED BY (\n  ").append(String.join(",\n  ", partitions)).append("\n)");
        }

        ddl.append(" STORED AS ").append(type).append(";");
        return ddl.toString();
    }
}
