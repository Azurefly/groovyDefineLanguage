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
 * Demo 2：跨源联邦查询（H2 + SQLite 内存表 Join）。
 *
 * <p>演示 GDL 的联邦能力：两个不同数据源的表分别执行，
 * 结果在内存中做 Hash Join。</p>
 */
public class FederatedDemo {
    public static void main(String[] args) {
        System.out.println("=== GDL Demo 2: 跨源联邦查询 ===");

        InMemoryEngine engine = new InMemoryEngine();

        // 数据源 A（模拟 H2）：用户表
        RowDataFrame users = new RowDataFrame(List.of(
                new ColumnInfo("user_id", "string"),
                new ColumnInfo("user_name", "string")));
        users.addRowValue(List.of("U01", "张三"));
        users.addRowValue(List.of("U02", "李四"));
        users.addRowValue(List.of("U03", "王五"));
        engine.registerTable("t_users", users);

        // 数据源 B（模拟 SQLite）：订单表
        RowDataFrame orders = new RowDataFrame(List.of(
                new ColumnInfo("order_id", "string"),
                new ColumnInfo("buyer_id", "string"),
                new ColumnInfo("amount", "double")));
        orders.addRowValue(List.of("O001", "U01", 120.5));
        orders.addRowValue(List.of("O002", "U02", 80.0));
        orders.addRowValue(List.of("O003", "U01", 200.0));
        orders.addRowValue(List.of("O004", "U03", 150.75));
        engine.registerTable("t_orders", orders);

        System.out.println("已注册用户表 t_users（" + users.rowSize() + " 行）");
        System.out.println("已注册订单表 t_orders（" + orders.rowSize() + " 行）");

        // 联邦 Join：用户 left join 订单
        // 注意：GDL 的 join 实现中，左表别名为 left_tbl、右表别名为 right_tbl
        CmdDataframe userDf = new CmdDataframeImpl(
                new FromOperator(new HiveDatasource(), "t_users"), engine);
        CmdDataframe orderDf = new CmdDataframeImpl(
                new FromOperator(new HiveDatasource(), "t_orders"), engine);

        CmdDataframe joined = userDf.leftJoin(orderDf, "left_tbl.user_id = right_tbl.buyer_id")
                .select("user_name", "order_id", "amount")
                .sort("user_name, order_id");

        RowDataFrame result = joined.collect();
        System.out.println("\n--- 用户-订单关联明细 ---");
        System.out.printf("%-10s %-10s %-10s%n", "user_name", "order_id", "amount");
        for (int i = 0; i < result.rowSize(); i++) {
            System.out.printf("%-10s %-10s %-10s%n",
                    result.getRow(i).getValue("user_name"),
                    result.getRow(i).getValue("order_id"),
                    result.getRow(i).getValue("amount"));
        }

        assert result.rowSize() == 4 : "应有 4 行关联结果";
        System.out.println("\nDemo 2 执行成功！");
    }
}
