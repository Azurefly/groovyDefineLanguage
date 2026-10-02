package com.pl.gdl.dataframe.llm;

import com.pl.gdl.common.model.ColumnInfo;
import com.pl.gdl.common.model.Row;
import com.pl.gdl.common.model.RowDataFrame;
import com.pl.gdl.dataframe.datasource.LlmDatasource;
import com.pl.gdl.dataframe.operator.advanced.LlmCallOperator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 执行 LlmCallOperator：对输入数据的每行调用大模型，结果追加为新列。
 */
public class LlmCallExecutor {

    /**
     * 执行 LLM 调用算子。
     *
     * @param operator LLM 调用算子
     * @param input    上游输入数据
     * @return 追加了结果列的数据框
     */
    public RowDataFrame execute(LlmCallOperator operator, RowDataFrame input) {
        LlmDatasource ds = (LlmDatasource) operator.getLlmDatasource();
        String url = ds.getUrl();
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("LlmDatasource 未配置 url，无法调用大模型");
        }

        Map<String, Object> params = operator.getModelParams();
        String apiKey = params != null ? String.valueOf(params.getOrDefault("apiKey", "")) : "";

        int concurrent = Math.max(1, ds.getConcurrent());
        ExecutorService pool = Executors.newFixedThreadPool(concurrent);

        try (LlmClient client = new LlmClient(url, apiKey, ds.getTimeout())) {
            List<CompletableFuture<String>> futures = new ArrayList<>();
            for (int i = 0; i < input.rowSize(); i++) {
                final Row row = input.getRow(i);
                final String prompt = buildPrompt(operator.getTarget(), row);
                futures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        return client.chat(operator.getModelName(), operator.getRole(), prompt, params);
                    } catch (Exception e) {
                        throw new RuntimeException("LLM 调用失败 (行 " + row + "): " + e.getMessage(), e);
                    }
                }, pool));
            }

            List<String> results = futures.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList());

            // 构造输出：原列 + 结果列
            List<ColumnInfo> outCols = new ArrayList<>(input.getColumns());
            outCols.add(new ColumnInfo(operator.getResultColumn(), "STRING"));
            RowDataFrame output = new RowDataFrame(outCols);
            for (int i = 0; i < input.rowSize(); i++) {
                Row inRow = input.getRow(i);
                Row outRow = new Row();
                for (ColumnInfo col : input.getColumns()) {
                    outRow.setValue(col.getColumnName(), inRow.getValue(col.getColumnName()));
                }
                outRow.setValue(operator.getResultColumn(), results.get(i));
                output.addRow(outRow);
            }
            return output;
        } finally {
            pool.shutdown();
        }
    }

    /**
     * 构建提示词：任务目标 + 行数据（JSON 形式）。
     */
    private String buildPrompt(String target, Row row) {
        StringBuilder sb = new StringBuilder();
        if (target != null && !target.isBlank()) {
            sb.append(target).append("\n");
        }
        sb.append("数据：").append(row.getValues());
        return sb.toString();
    }
}
