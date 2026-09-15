package com.pl.gdl.dataframe.federation;

import com.pl.gdl.common.model.RowDataFrame;
import com.pl.gdl.dataframe.datasource.DatasourceIdentity;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/** Ordered exchange registry. Custom strategies may be registered ahead of MEMORY. */
public class RowExchangeRegistry {
    private static final RowExchangeRegistry DEFAULT = new RowExchangeRegistry(true);
    private final CopyOnWriteArrayList<RowExchange> exchanges = new CopyOnWriteArrayList<>();

    public RowExchangeRegistry() {
        this(true);
    }

    private RowExchangeRegistry(boolean defaults) {
        if (defaults) exchanges.add(new MemoryRowExchange());
    }

    public static RowExchangeRegistry getDefault() { return DEFAULT; }

    public RowExchangeRegistry registerFirst(RowExchange exchange) {
        exchanges.add(0, Objects.requireNonNull(exchange, "exchange must not be null"));
        return this;
    }

    public RowExchangeRegistry register(RowExchange exchange) {
        exchanges.add(Objects.requireNonNull(exchange, "exchange must not be null"));
        return this;
    }

    public List<RowExchange> getExchanges() { return List.copyOf(exchanges); }

    public RowExchange select(DatasourceIdentity source, DatasourceIdentity target, RowDataFrame rows) {
        return exchanges.stream()
                .filter(exchange -> exchange.supports(source, target, rows))
                .findFirst()
                .orElseThrow(() -> new UnsupportedOperationException(
                        "No exchange strategy supports " + source + " -> " + target));
    }
}
