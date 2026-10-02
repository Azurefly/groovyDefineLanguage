package com.pl.gdl.dataframe;

import com.pl.gdl.common.model.ColumnInfo;
import com.pl.gdl.common.model.RowDataFrame;
import com.pl.gdl.dataframe.dataframe.CmdDataframe;
import com.pl.gdl.dataframe.dataframe.CmdDataframeImpl;
import com.pl.gdl.dataframe.datasource.CmdDatasource;
import com.pl.gdl.dataframe.datasource.DatasourceRegistry;
import com.pl.gdl.dataframe.datasource.HiveDatasource;
import com.pl.gdl.dataframe.datasource.LlmDatasource;
import com.pl.gdl.dataframe.engine.InMemoryEngine;
import com.pl.gdl.dataframe.operator.base.FromOperator;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * LLM 真实全流程测试：通过 GDL 的 llmCall 算子调用真实大模型服务（Ollama 本地部署），
 * 对每条记录执行大模型推理，结果追加为新列。
 *
 * 运行方式：
 * 1. 启动 Ollama 服务并拉取模型：ollama serve &amp;&amp; ollama pull qwen2:0.5b
 * 2. 设置环境变量 LLM_TEST_URL（如 http://127.0.0.1:11434/v1/chat/completions）
 *    和 LLM_TEST_MODEL（如 qwen2:0.5b）
 * 3. 运行：mvn test -Dtest=RealLlmE2ETest
 *
 * 未设置 LLM_TEST_URL 时自动跳过。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RealLlmE2ETest {

    private static final String LLM_URL = System.getenv("LLM_TEST_URL");
    private static final String LLM_MODEL = System.getenv().getOrDefault("LLM_TEST_MODEL", "qwen2:0.5b");

    @BeforeAll
    static void setup() {
        assumeTrue(LLM_URL != null && !LLM_URL.isBlank(),
                "未设置 LLM_TEST_URL，跳过真实 LLM 测试");
        System.out.println("LLM 服务地址: " + LLM_URL + ", 模型: " + LLM_MODEL);
    }

    @Test
    @Order(1)
    public void llmProviderCreatesLlmDatasource() {
        DatasourceRegistry registry = DatasourceRegistry.getDefault();
        CmdDatasource ds = registry.create("LLM", Map.of("url", LLM_URL, "concurrent", 2));
        assertThat(ds).isInstanceOf(LlmDatasource.class);
        assertThat(((LlmDatasource) ds).getUrl()).isEqualTo(LLM_URL);
        System.out.println("LLM provider 创建成功: " + ds);
    }

    @Test
    @Order(2)
    public void llmCallRealInference() {
        // 准备输入数据：3 条待分类的文本
        InMemoryEngine engine = new InMemoryEngine();
        ColumnInfo c1 = new ColumnInfo("id", "int");
        ColumnInfo c2 = new ColumnInfo("text", "string");
        RowDataFrame data = new RowDataFrame(List.of(c1, c2));
        data.addRowValue(List.of("1", "今天天气真好，适合出去散步"));
        data.addRowValue(List.of("2", "这部电影太无聊了，浪费时间"));
        data.addRowValue(List.of("3", "这家餐厅的菜味道不错"));

        engine.registerTable("t_llm_input", data);

        // 构建 LLM 数据源
        DatasourceRegistry registry = DatasourceRegistry.getDefault();
        LlmDatasource llmDs = (LlmDatasource) registry.create("LLM",
                Map.of("url", LLM_URL, "concurrent", 2));

        // 执行 llmCall：情感分类
        CmdDataframe df = new CmdDataframeImpl(
                new FromOperator(new HiveDatasource(), "t_llm_input"), engine)
                .llmCall(llmDs, LLM_MODEL,
                        "你是一个情感分析助手",
                        "判断下面数据的 text 字段是正面、负面还是中性情感，只回答\"正面\"、\"负面\"或\"中性\"三个词中的一个，不要解释。",
                        "sentiment",
                        Map.of("temperature", 0.1));

        RowDataFrame result = df.collect();

        // 验证：3 行都有结果，结果列非空
        assertThat(result.rowSize()).isEqualTo(3);
        System.out.println("=== LLM 真实推理结果 ===");
        for (int i = 0; i < result.rowSize(); i++) {
            Object id = result.getRow(i).getValue("id");
            Object text = result.getRow(i).getValue("text");
            Object sentiment = result.getRow(i).getValue("sentiment");
            System.out.println("id=" + id + ", text=" + text + ", sentiment=" + sentiment);
            assertThat(sentiment).isNotNull();
            assertThat(sentiment.toString().trim()).isNotBlank();
        }
        System.out.println("=== LLM 真实推理完成 ===");
    }
}
