package com.pl.gdl.dataframe.federation;

import com.pl.gdl.common.model.Row;
import com.pl.gdl.common.model.RowDataFrame;
import com.pl.gdl.dataframe.datasource.DatasourceIdentity;

import java.util.ArrayList;

/** Default same-area exchange. Copies the frame to make the materialization boundary explicit. */
public class MemoryRowExchange implements RowExchange {
    @Override public String getName() { return "memory"; }
    @Override public Mode getMode() { return Mode.MEMORY; }

    @Override
    public boolean supports(DatasourceIdentity source, DatasourceIdentity target, RowDataFrame rows) {
        return source != null && target != null && source.sameArea(target);
    }

    @Override
    public RowDataFrame transfer(DatasourceIdentity source, DatasourceIdentity target, RowDataFrame rows) {
        if (!supports(source, target, rows)) {
            throw new UnsupportedOperationException("Memory exchange only supports same-area transfer: "
                    + source + " -> " + target);
        }
        RowDataFrame copy = new RowDataFrame(rows == null ? null : rows.getColumns());
        if (rows != null) {
            for (Row row : rows.getRows()) {
                copy.addRowValue(row.getValues());
            }
        }
        return copy;
    }
}
