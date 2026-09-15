package com.pl.gdl.common.model;

import com.pl.gdl.common.enums.DataType;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RowDataFrameTest {

    @Test
    public void testRowMutationAndIteration() {
        ColumnInfo c1 = new ColumnInfo("id", "int");
        ColumnInfo c2 = new ColumnInfo("name", "string");
        RowDataFrame df = new RowDataFrame(List.of(c1, c2));

        df.addRowValue(List.of(1, "Alice"));
        df.addRowValue(List.of(2, "Bob"));

        assertThat(df.rowSize()).isEqualTo(2);

        Row row1 = df.getRow(0);
        assertThat((Integer) row1.getValue("id")).isEqualTo(1);
        assertThat((String) row1.getValue("name")).isEqualTo("Alice");

        row1.setValue("name", "Alice_Updated");
        assertThat((String) row1.getValue("name")).isEqualTo("Alice_Updated");

        int count = 0;
        for (Row row : df) {
            count++;
            assertThat((Object) row.getValue("id")).isNotNull();
        }
        assertThat(count).isEqualTo(2);
    }

    @Test
    public void testIteratorCannotRemoveInternalRows() {
        RowDataFrame df = new RowDataFrame(List.of(new ColumnInfo("id", "int")));
        df.addRowValue(List.of(1));

        Iterator<Row> iterator = df.iterator();
        assertThat(iterator.next().getValue("id")).isEqualTo(1);
        assertThatThrownBy(iterator::remove)
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(df.rowSize()).isEqualTo(1);
    }

    @Test
    public void testDataTypeMapping() {
        assertThat(DataType.fromString("string")).isEqualTo(DataType.VARCHAR);
        assertThat(DataType.fromString("int")).isEqualTo(DataType.INTEGER);
        assertThat(DataType.fromString("bigint")).isEqualTo(DataType.BIGINT);
        assertThat(DataType.fromString("timestamp")).isEqualTo(DataType.TIMESTAMP);
    }
}
