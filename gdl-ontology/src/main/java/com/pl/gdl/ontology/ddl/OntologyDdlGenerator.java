package com.pl.gdl.ontology.ddl;

import com.pl.gdl.common.util.SqlSanitizer;
import com.pl.gdl.ontology.annotation.Column;
import com.pl.gdl.ontology.annotation.Table;
import com.pl.gdl.ontology.model.Ontology;
import com.pl.gdl.ontology.registry.OntologyMetadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.StringJoiner;

/**
 * 本体 Hive DDL 生成器：根据 {@link Ontology} 子类的注解生成建表语句。
 *
 * <p>注入防护：表名 / 列名统一经 {@link SqlSanitizer#quoteIdentifier(String)} 包裹，
 * 备注经 {@link SqlSanitizer#escapeLiteral(String)} 转义；{@code STORED AS} 后为
 * Hive 关键字，走白名单校验（未知格式直接抛 {@link IllegalArgumentException}，
 * 不能加引号）。</p>
 */
public class OntologyDdlGenerator {

    /** Hive 支持的文件存储格式白名单（STORED AS 后为关键字，不加引号）。 */
    private static final Set<String> ALLOWED_STORAGE_FORMATS = Set.of(
            "ORC", "PARQUET", "TEXTFILE", "AVRO", "SEQUENCEFILE", "RCFILE"
    );

    public static String generateHiveDdl(OntologyMetadata metadata) {
        Table table = metadata.getTableAnnotation();
        String type = table != null ? table.type() : "ORC";
        String remarks = table != null ? table.remarks() : "";

        String tableName = SqlSanitizer.quoteIdentifier(metadata.getOntologyClass().getSimpleName().toLowerCase());

        StringJoiner colsSj = new StringJoiner(",\n  ");
        List<String> partitions = new ArrayList<>();

        for (OntologyMetadata.FieldMetadata fm : metadata.getFields()) {
            Column col = fm.getColumnAnnotation();
            String colName = col != null && !col.cName().isEmpty() ? col.cName() : fm.getFieldName();
            String quotedColName = SqlSanitizer.quoteIdentifier(colName);
            String dt = col != null ? col.dataTypeName().toUpperCase(Locale.ROOT) : "STRING";
            String colRemarks = col != null && !col.remarks().isEmpty()
                    ? " COMMENT '" + SqlSanitizer.escapeLiteral(col.remarks()) + "'"
                    : "";

            if (col != null && col.partition()) {
                partitions.add(quotedColName + " " + dt + colRemarks);
            } else {
                colsSj.add(quotedColName + " " + dt + colRemarks);
            }
        }

        StringBuilder ddl = new StringBuilder();
        ddl.append("CREATE TABLE IF NOT EXISTS ").append(tableName).append(" (\n  ")
           .append(colsSj)
           .append("\n)");

        if (!remarks.isEmpty()) {
            ddl.append(" COMMENT '").append(SqlSanitizer.escapeLiteral(remarks)).append("'");
        }

        if (!partitions.isEmpty()) {
            ddl.append(" PARTITIONED BY (\n  ").append(String.join(",\n  ", partitions)).append("\n)");
        }

        ddl.append(" STORED AS ").append(normalizeStorageFormat(type)).append(";");
        return ddl.toString();
    }

    /**
     * 校验并规范化 Hive 存储格式；未知格式抛 {@link IllegalArgumentException}。
     */
    static String normalizeStorageFormat(String type) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Hive 存储格式不能为空");
        }
        String normalized = type.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_STORAGE_FORMATS.contains(normalized)) {
            throw new IllegalArgumentException("不支持的 Hive 存储格式: " + type
                    + "，允许的格式为 " + ALLOWED_STORAGE_FORMATS);
        }
        return normalized;
    }
}
