package com.pl.gdl.demo;

import com.pl.gdl.common.model.ColumnInfo;
import com.pl.gdl.common.model.RowDataFrame;
import com.pl.gdl.dataframe.dataframe.CmdDataframe;
import com.pl.gdl.dataframe.dataframe.CmdDataframeImpl;
import com.pl.gdl.dataframe.datasource.DatasourceRegistry;
import com.pl.gdl.dataframe.datasource.HiveDatasource;
import com.pl.gdl.dataframe.datasource.LlmDatasource;
import com.pl.gdl.dataframe.engine.InMemoryEngine;
import com.pl.gdl.dataframe.operator.base.FromOperator;

import java.util.List;
import java.util.Map;

/**
 * Demo 3：LLM 大模型调用。
 *
 * <p>演示 GDL 的 llmCall 算子：对每条记录调用大模型做情感分析，
 * 结果追加为新列。</p>
 *
 * <p>前置条件：启动 Ollama 并拉取模型
 * <pre>
 *   ollama serve &amp;
 *   ollama pull qwen2:0.5b
 * </pre>
 * 可通过环境变量覆盖：{@code LLM_URL}、{@code LLM_MODEL}。</p>
 */
public class LlmDemo {
    public static void main(String[] args) {
        System.out.println("=== GDL Demo 3: LLM 大模型调用 ===");

        String llmUrl = System.getenv().getOrDefault("LLM_URL",
                "http://127.0.0.1:11434/v1/chat/completions");
        String llmModel = System.getenv().getOrDefault("LLM_MODEL", "qwen2:0.5b");
        System.out.println("LLM 服务: " + llmUrl);
        System.out.println("模型: " + llmModel);

        // 1. 准备评论数据
        InMemoryEngine engine = new InMemoryEngine();
        RowDataFrame comments = new RowDataFrame(List.of(
                new ColumnInfo("id", "string"),
                new ColumnInfo("comment", "string")));
        comments.addRowValue(List.of("1", "这家店的服务态度非常好，下次还来"));
        comments.addRowValue(List.of("2", "物流太慢了，等了一周才到"));
        comments.addRowValue(List.of("3", "产品质量还行，性价比不错"));
        engine.registerTable("t_comments", comments);

        // 2. 构建 LLM 数据源
        DatasourceRegistry registry = DatasourceRegistry.getDefault();
        LlmDatasource llmDs = (LlmDatasource) registry.create("LLM",
                Map.of("url", llmUrl, "concurrent", 2));

        // 3. 执行 llmCall
        CmdDataframe df = new CmdDataframeImpl(
                new FromOperator(new HiveDatasource(), "t_comments"), engine)
                .llmCall(llmDs, llmModel,
                        "你是一个情感分析助手",
                        "判断 comment 字段是正面、负面还是中性，只回答\"正面\"、\"负面\"或\"中性\"三个词中的一个，不要解释。",
                        "sentiment",
                        Map.of("temperature", 0.1));

        RowDataFrame result;
        try {
            result = df.collect();
        } catch (Exception e) {
            System.err.println("\n[错误] LLM 调用失败: " + rootCause(e));
            System.err.println("\n请检查:");
            System.err.println("  1. Ollama 是否已启动: ollama serve &");
            System.err.println("  2. 模型是否已拉取: ollama pull " + llmModel);
            System.err.println("  3. 服务地址是否正确: " + llmUrl);
            System.err.println("  可通过环境变量覆盖: LLM_URL、LLM_MODEL");
            System.exit(1);
            return;
        }

        // 4. 打印结果
        System.out.println("\n--- 情感分析结果 ---");
        System.out.printf("%-4s %-40s %-10s%n", "id", "comment", "sentiment");
        for (int i = 0; i < result.rowSize(); i++) {
            System.out.printf("%-4s %-40s %-10s%n",
                    result.getRow(i).getValue("id"),
                    result.getRow(i).getValue("comment"),
                    String.valueOf(result.getRow(i).getValue("sentiment")).trim());
        }

        System.out.println("\nDemo 3 执行成功！");
    }

    private static String rootCause(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        String msg = t.getMessage();
        return msg != null ? msg : t.getClass().getSimpleName();
    }
}
