# 输出与 SQL 算子

---

## to() — 输出算子

将数据集写入目标数据源的表中，支持插入、覆盖、分区、upsert、视图等多种模式。

```groovy
// 基础语法
def 数据集 = 数据集.to(数据源, "目标表名")
    [.fields("字段定义")]      // 表不存在时按此结构建表
    [.ttl(秒数)]               // 数据淘汰周期（秒）
    [.overwrite()]             // 覆盖全表
    [.overwrite("partition")]  // 覆盖指定分区
    [.partition("分区表达式")]  // 插入到分区
    [.upsert()]                // upsert 方式写入
    [.view()]                  // 写入视图表
```

| 场景 | 写法 |
|---|---|
| 普通插入 | `dataset.to(hiveDs, "t_user")` |
| 建表插入 | `dataset.to(hiveDs, "t_user").fields("id int, name string")` |
| 设置 TTL | `dataset.to(hiveDs, "t_user").ttl(86400)` |
| 覆盖全表 | `dataset.to(hiveDs, "t_user").overwrite()` |
| 覆盖指定分区（FRC表） | `dataset.to(hiveDs, "t_user").overwrite("partition").partition("p1='20250719'")` |
| 清空全表分区后覆盖 | `dataset.to(hiveDs, "t_user").overwrite().partition("p1='final'")` |
| Upsert | `dataset.to(hiveDs, "t_user").upsert()` |
| 视图 | `dataset.to(hiveDs, "t_user").view()` |
| 视图+建表 | `dataset.to(hiveDs, "t_user").fields("id int,name string comment '姓名'").view()` |

### overwriteTo() — 覆盖写入简写

`overwriteTo()` 是 `to().overwrite()` 的简写形式，常用于 http 算子场景。

```groovy
// 等价于 .to(hiveDs, "dw.t_result").overwrite()
def df = http("GET", "http://api.example.com/data")
    .response("/data", true, [[...]])
    .overwriteTo(hiveDs, "dw.t_result")
```

### 分区覆盖语义说明

| 写法 | 含义 |
|---|---|
| `.overwrite()` | 清空全表后插入 |
| `.overwrite().partition("p1='final'")` | 清空全表所有分区，再插入到指定分区 |
| `.overwrite("partition").partition("p1='20250719'")` | 仅清空指定分区（FRC 分区表），再插入 |
| `.partition("p1='final'")` | 不清空，追加插入到指定分区 |

### fields() 与流表输出

当输出到流表（实时场景）时，必须通过 `fields()` 显式声明字段结构：

```groovy
dataset.to(hiveDs, "dw.t_stream_result")
    .fields("id string, name string, total bigint")
```

---

## query() — 原生 SQL 查询

在指定数据源执行原生 SQL 查询语句。

```groovy
// 语法
def 数据集 = query(数据源, "查询SQL")[.depend(dataset1)]

// 示例
def dataset = query(pgDs, "select * from t_user where user_id='xxx'")

// 引用临时表名（前置结果不主动落库）
def df1 = from(pgDs, "t_test").select("f1, f2, f3")
def df2 = query(pgDs, "select * from ${df1.tempTable}").depend(df1)

// 也可使用 VAR_TEMP_TABLE（两者等价）
def df2 = query(pgDs, "select * from ${df1.VAR_TEMP_TABLE}").depend(df1)

// 有依赖（等待前置步骤执行完）
def node4 = node3.to(hiveDs, "t_test")
def dataset = query(hiveDs, "select * from t_test").depend(node4)
```

---

## insert() — 原生 SQL 插入

执行原生 INSERT 或 UPSERT 语句。

```groovy
// 语法
def 数据集 = insert(数据源, "目标表", "插入SQL")[.depend(df)]

// 示例
def df0 = insert(hiveDs, "dw.t_test", "insert into t_test(insert_date) values('2025-04-03')")

// 有依赖
def df1 = query(hiveDs, "select * from t_test").to(hiveDs, "t_temp")
def df2 = insert(hiveDs, "dw.t_result", "insert into t_result(insert_date) values('2025-04-03')").depend(df1)
```

---

## returnDf() — 返回结果集

将计算结果返回给调用客户端（默认返回 100 条）。

```groovy
def dataset = query(pgDs, "select * from t_user where user_id='xxx'")
returnDf(dataset)
```

---

## groovy() — Groovy 脚本算子

在 GDL 中内嵌 Groovy 脚本处理数据集，适合复杂自定义逻辑。

```groovy
// 有输入数据集
def 数据集2 = 数据集.groovy(dataFrame -> {
    dataFrame.forEach { row -> {
        String name = row.getValue("name")
        row.setValue("name", name.toUpperCase())
    }}
    return dataFrame
}).to(hiveDs, "result_table")

// 无输入数据集（构造新数据集）
def 数据集 = groovy(dataFrame -> {
    List<ColumnInfo> cols = [...]
    RowDataFrame newDf = new RowDataFrame(cols)
    // ... 填充数据
    return newDf
}).to(hiveDs, "result_table")

// 无需返回结果（副作用操作）
groovy(dataFrame -> {
    // 发送通知、写文件等
    return null
})
```

**RowDataFrame 构造方式：**

```groovy
List<ColumnInfo> columnInfos = new ArrayList<>()
ColumnInfo col = new ColumnInfo()
col.setColumnName("id")
col.setDataTypeName("VARCHAR")
col.setColumnLength(50)
columnInfos.add(col)
RowDataFrame newDf = new RowDataFrame(columnInfos)
newDf.addRowValue(["值1", "值2"])
```

---

## python() — Python 脚本算子

执行 Python 脚本处理数据，通过 `load_table()` 加载数据，返回 `pd.DataFrame`。

```groovy
// 语法
def 数据集 = python(数据源, "输出表名", """python脚本内容""")

// 示例
def dataset = python(hiveDs, "dw.result_table", """
def main() -> pd.DataFrame:
    input_df = load_table('dw.test_input', limit=100)
    input_df['C'] = input_df['A'] + input_df['B']
    return input_df
""")
```
