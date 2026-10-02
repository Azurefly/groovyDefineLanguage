package com.pl.gdl.demo;

import com.pl.gdl.common.model.ColumnInfo;
import com.pl.gdl.common.model.RowDataFrame;
import com.pl.gdl.dataframe.dataframe.CmdDataframe;
import com.pl.gdl.dataframe.dataframe.CmdDataframeImpl;
import com.pl.gdl.dataframe.datasource.HiveDatasource;
import com.pl.gdl.dataframe.engine.InMemoryEngine;
import com.pl.gdl.dataframe.operator.base.FromOperator;

import java.util.List;

/**
 * Demo 1：H2 内存 ETL 全流程。
 *
 * <p>演示 GDL 的核心能力：内存表注册 → where 过滤 → select 投影 →
 * withColumn 衍生列 → group 聚合 → sort 排序 → limit 取 TopN。</p>
 *
 * <p>运行：{@code mvn -o compile exec:java -Dexec.mainClass=com.pl.gdl.demo.H2EtlDemo}
 * 或直接运行 main 方法。</p>
 */
public class H2EtlDemo {
    public static void main(String[] args) {
        System.out.println("=== GDL Demo 1: H2 内存 ETL 全流程 ===");

        // 1. 准备订单数据
        InMemoryEngine engine = new InMemoryEngine();
        RowDataFrame orders = new RowDataFrame(List.of(
                new ColumnInfo("order_id", "string"),
                new ColumnInfo("user_id", "string"),
                new ColumnInfo("amount", "double"),
                new ColumnInfo("status", "string")));
        orders.addRowValue(List.of("O001", "U01", 120.5, "paid"));
        orders.addRowValue(List.of("O002", "U02", 80.0, "paid"));
        orders.addRowValue(List.of("O003", "U01", 200.0, "refunded"));
        orders.addRowValue(List.of("O004", "U03", 150.75, "paid"));
        orders.addRowValue(List.of("O005", "U02", 300.0, "paid"));
        orders.addRowValue(List.of("O006", "U01", 50.25, "paid"));
        engine.registerTable("t_orders", orders);
        System.out.println("已注册订单表 t_orders，共 " + orders.rowSize() + " 行");

        // 2. ETL：过滤已支付 → 只取需要的列 → 按用户聚合 → 排序取 Top
        CmdDataframe df = new CmdDataframeImpl(
                new FromOperator(new HiveDatasource(), "t_orders"), engine)
                .where("status = 'paid'")
                .select("user_id", "amount")
                .group("user_id", "sum(amount) as total_amount, count(*) as order_cnt")
                .sort("total_amount desc")
                .limit(10);

        // 3. 执行并打印结果
        RowDataFrame result = df.collect();
        System.out.println("\n--- 用户消费 Top 榜 ---");
        System.out.printf("%-10s %-15s %-10s%n", "user_id", "total_amount", "order_cnt");
        for (int i = 0; i < result.rowSize(); i++) {
            System.out.printf("%-10s %-15s %-10s%n",
                    result.getRow(i).getValue("user_id"),
                    result.getRow(i).getValue("total_amount"),
                    result.getRow(i).getValue("order_cnt"));
        }

        // 4. 验证结果
        assert result.rowSize() == 3 : "应有 3 个用户";
        System.out.println("\nDemo 1 执行成功！");
    }
}
