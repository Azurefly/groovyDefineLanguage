# TSML指导手册V1.4

文档管理

| 版本 | 状态 | 日期     | 负责人 | 更改原由                   |
| ---- | ---- | -------- | ------ | -------------------------- |
| 2.0  | 修订 | 20251118 | 任会   | 调整增量信号算子语法及示例 |


## 1 简介

    TSML（TaoSha Model Language）是我司用于服务建模工具、治理工具等产品而设计的一门模型语言，该模型语言能力覆盖淘沙产品、BDOS产品的模型定义和逻辑编排。

## 2 语法规范

淘沙场景下的TSML语法示例：

```groovy
// 定义fmdb数据源
def fmdbDs = fmdb()
// 定义数据集
def a = from(fmdbDs, "PHY_ADM_VMODEL_RES").alias("a").nodeId("1")
def b = from(fmdbDs, "PHY_ADM_VMODEL_RES2").alias("b").nodeId("2")
// 淘沙界面提取算子
def df1 = a.select("xuhao, lower(xuhao) as xuhao_lower")
  .where("xuhao_lower != '123'").nodeId("3")
// 淘沙界面统计算子
def df2 = df1.group("xuhao", "sum(xuhao) as xuhao_sum")
  .select("xuhao as xuhao, xuhao_sum as xuhao_sum").nodeId("4")
// 淘沙界面并集算子
def df3 = df2.union(b).nodeId("5")
// 淘沙界面排序算子
def df4 = df3.sort("xuhao_sum desc").limit(10).nodeId("6")
// 淘沙界面输出算子
def df5 = df4.to(fmdbDs, "t_ad_result").nodeId("7")
```

### 2.1 数据资源定义

#### 2.1.1 数据源定义

**fmdb()**

```groovy
/**
 * 【语法说明】
 * fmdb数据源定义 fmdb("dsConfName")
 * dsConfName：数据源配置目录名称,
 *   数据源配置信息和认证文件统一放在resource/fmdb/{dsConfName}目录中,
 *   数据源配置需要提供fmdbDruid.properties,krb5.conf,{user}.keytab,core-site.xml,hdfs-site.xml配置文件
 *   当dsConfName不填时，使用resource/fmdb/default目录中的fmdb数据源,指令为fmdb(),等效fmdb("default")指令
 * areaCode：表示数据源的归属地，用于漂移计算，是可选属性，不设置默认为本地数据源
 * 返回值：为自定义数据源的名称，后续引用可使用该名称代替。
 */
def 数据源 = fmdb(["dsConfName"])[.areaCode("地市编码")]

/**【用法示例】**/
//本地fmdb数据源定义
def fmdbLocalDs = fmdb("dsConfName") // 加载 resource/fmdb/dsConfName配置目录中的fmdb配置信息
def fmdbLocalDs = fmdb("/home/xxx/dsConfName") // 加载指定绝对路径的fmdb配置信息
def fmdbLocalDs = fmdb() // 加载 resource/fmdb/default配置目录中的fmdb配置信息,和fmdb("default")指令等效
//非本地的fmdb数据源定义
def fmdbNJDs = fmdb("dsConfName").areaCode("320100")
def fmdbNJDs = fmdb().areaCode("320100") // 使用异地tre默认fmdb数据源
```

**postgres()**

```groovy
/**
 * 【语法说明】
 * postgres(host, port, database, username, password)：定义PostgreSQL数据源。
     - host：数据库IP地址
     - port：端口
     - database：数据库
     - username：用户名
     - password：密码
     - areaCode：表示数据源的归属地，用于漂移计算，是个可选属性，不设置默认为本地数据源
 * 返回值：自定义数据源名称，后续引用可使用该名称代替。
 */
def 数据源 = postgres(host,port,database,username,password)[.areaCode("地市编码")]

/**【用法示例】**/
//本pg
def pg = postgres("172.16.31.206", 15432, "fenghuo", "${pgUserName}", "${pgPassword}")
//需要漂移pg
def pg = postgres("172.16.31.206", 15432, "fenghuo", "${pgUserName}", "${pgPassword}").areaCode("320100")
```

**hivehw()**

```groovy
/**
 * 【语法说明】
 * 华为hive数据源定义 hivehw("dsConfName"), tre1.2.x版本对接了华为hive 3.1.x
 * dsConfName：数据源配置目录名称,
 *   数据源配置信息和认证文件统一放在resource/hivehw/{dsConfName}目录中,
 *   数据源配置需要提供hiveclient.properties,krb5.conf,user.keytab,core-site.xml,hdfs-site.xml配置文件
 *   当dsConfName不填时，使用resource/hivehw/default目录中的华为hive数据源,指令为hivehw(),等效hivehw("default")指令
 * areaCode：表示数据源的归属地，用于漂移计算，是可选属性，不设置默认为本地数据源
 * 返回值：为自定义数据源的名称，后续对数据源的引用，可用数据源名称代替 
 */
def 数据源 = hivehw(["dsConfName"])[.areaCode("地市编码")]

/**【用法示例】**/
//本地华为hive数据源定义
def hivehwLocalDs = hivehw("dsConfName") // 加载 resource/hivehw/dsConfName配置目录中的华为hive配置信息
def hivehwLocalDs = hivehw("/home/xxx/dsConfName") // 加载指定绝对路径的华为hive配置信息
def hivehwLocalDs = hivehw() // 加载 resource/hivehw/default配置目录中的华为hive配置信息,和hivehw("default")指令等效
//非本地的华为hive数据源定义
def hivehwNJDs = hivehw("dsConfName").areaCode("320100")
def hivehwNJDs = hivehw().areaCode("320100") // 使用异地tre默认华为hive数据源
```
#### 2.1.2 数据集定义

```groovy
/**
 * 【语法说明】
 * 数据源：表示要提取的数据源名称，用变量引用表示
 * 表名：表示数据源中要提取的表名，用字符串表示
 * depend：(可选) 需要依赖的算子对象
 * alias：表示用于给数据集定义别名，类似sql语法中对表名进行定义别名
 * 返回值：为自定义数据集名称，后续对数据集的引用，可用数据集名称代替 
 */
def 数据集 = from(数据源,"[库名.]表名")[.alias("别名")][.depend(dataset1[,dataset2])]

/**【用法示例】**/
//数据集dataset
def dataset = from(fmdbDs, "t_person")
def dataset = from(fmdbDs, "t_person").alias("a")
//查询tre库的表
def dataset = from(fmdbDs, "tre.t_person")
//依赖数据集，先入库再查询
def df0 = insert(postgresqlDs, "tre.t_person", "insert into t_person(insert_date) values('2025-04-03')")
def dataset = from(postgresqlDs, "tre.t_person").depend(df0)
```

#### 2.1.3 节点ID定义

数据集节点ID定义，用于解决算子和应用层画布上节点的对应关系表示，也用于TRE引擎给画布同步节点运行实时状态、数据量统计等相关信息，同一个画布内的nodeId必须是唯一的，语法如下：

```groovy
/**
 * 【语法说明】
 * 数据集：通过数据集变量来表示。
 * nodeId：图上节点对应的唯一标识符，在同一画布内必须是唯一的。nodeId由节点类型-编号组成。
 * 示例：淘沙的提取节点可能对应where和select两个操作，其样例代码如下:
 *         df = df.where().select().nodeId("提取-1001")
 * 返回值：返回自定义的数据集名称；之后可以通过数据集变量来引用该数据集。
 */
def 数据集 = 数据集.nodeId("nodeId")

/**【用法示例】**/
//对数据集进行过滤
def filteredDf = a.select("xuhao, lower(xuhao) as xuhao_lower")
  			.where("xuhao_lower != '123'").nodeId("提取-0001")
```

### 2.2 算子类语法

算子类语法的作用对象均为数据集dataset，算子返回对象也是数据集dataset，多个算子连续写法，支持类似函数的链式调用，例如：


```groovy
//数据集dataset
def dataset = from(fmdbDs, "t_person").select("age, name").where("name != '张三'")
//带别名的数据集dataset
def dataset = from(fmdbDs, "t_person").alias("a").select("a.age, a.name").where("a.name != '张三'")
```

#### 2.2.1 过滤算子

**where()**

针对数据集的行内容进行过滤，用法如下：

```groovy
/**
 * 【语法说明】
 * 数据集：用数据集变量引用表示
 * 过滤条件：遵循sql的where条件语法规范
 * 返回值：为自定义数据集名称，后续对数据集的引用，可用数据集变量代替 
 */
def 数据集 = 数据集.where("过滤条件")

/**【用法示例】**/
//对数据集进行过滤
def filteredDataset = dataset.where("age >0 and age < 150")
```

**select()**

针对数据集的列（字段）进行过滤，用法如下：

```groovy
/**
 * 【语法说明】
 * 数据集：用数据集变量引用表示
 * 过滤字段表达式：遵循sql的select语法规范，多个字段可用逗号隔开的一个参数传递，也可以多个参数传递
 * 返回值：为自定义数据集，后续对数据集的引用，可用数据集变量代替 
 */
def 数据集 = 数据集.select("过滤字段表达式1"[,"过滤字段表达式2"])

/**【用法示例】**/
//对数据集进行过滤
def dataset = dataset.select("age","name as person_name","replace(email,'@fh.com','@fiberhome.com') as email")
```

**mapping()**

针对数据集的列（字段）进行过滤，类同select()，更直观的展示过滤字段和其别名，用法如下：

```groovy
/**
 * 【语法说明】
 * 数据集：用数据集变量引用表示
 * 过滤字段表达式：遵循sql的select语法规范，多个字段可用逗号隔开的一个参数传递
 * 返回值：为自定义数据集，后续对数据集的引用，可用数据集变量代替 
 */
def 数据集 = 数据集.mapping(别名: "过滤字段表达式1"[,别名: "过滤字段表达式1"])

/**【用法示例】**/
//对数据集进行过滤
def dataset = dataset.mapping(
  age: "age",
  person_name: "name",
  email: "replace(email,'@fh.com','@fiberhome.com')"
)
```

#### 2.2.2 列操作算子

**withColumn()**

支持对特定列进行计算，也可使用此方法扩展新列，用法如下：

```groovy
/**
 * 【语法说明】
 * 字段名称：要操作或扩展的字段名称
 * 操作表达式：对字段操作的表达式
 * 字段类型（可选）：新字段类型
 * 返回值：返回列操作之后的新数据集
 */
def 数据集 = 数据集.withColumn("字段名称", "操作表达式"[,"字段类型"])

/**【用法示例】**/
//修改原列
def dataset = dataset.withColumn("mobile", "concat('+86', mobile)")
//新增列
def dataset = dataset.withColumn("birthday", "subString(idCard,6, 13)")
//分列（带类型）
def newdf = df.withColumn("age", "split(column,'[\\t;,]',2)[0]","String")
//四则运算 （+ - * / 要求运算符号两侧类型一致）
def newdf = df.withColumn("new_column","column + 1")

```

#### 2.2.3 聚合算子

**group()**

支持分组聚合，并使用各类聚合算子，用法如下：

```groovy
/**
 * 【语法说明】
 * 分组字段：支持直接指定字段名称，多个字段逗号隔开，也可以嵌套函数，函数遵循sql规范
 * 聚合表达式：聚合表达式中可以写1个或多个聚合函数表达式，表达式规范遵循sql规范
 * 目前支持的函数的有：count()、min()、max()、sum()、avg()
 * 返回值：返回聚合操作之后的新数据集
 */
def 数据集 = 数据集.group("分组字段", "聚合表达式")

/**【用法示例】**/
// 数据集分组, 按照身份证号分组, 统计每个人的手机号数量
def datasetA = dataset.group("sex", "count(sex) as count_sex")
//SQL语法中group后的having，等价于where，使用where代替
def datasetB = dataset.group("sex", "count(sex) as count_sex").where("count_sex > 10")
def datasetC = dataset.group("class,sex", "avg(age) as ave_age")
// 合并行操作
/**
 * concat_ws 函数说明
 * 样例：concat_ws('###',sort_array(collect_list(mobile))) 
 * ###：合并行的字段内容的分隔符,mobile：合并行的字段
 */
def datasetD = dataset.group("id_no,name,age", "concat_ws('###',sort_array(collect_list(mobile))) as mobile")


```

#### 2.2.4 排序算子

**sort()**

对记录进行排序，用法如下：

```groovy
/**
 * 【语法说明】
 * 排序字段：字段名 asc|desc
 * index （可选）: 在淘沙场景下，排序后的数据集中，会增加一列序号字段。
 * 返回值：返回排序后的新数据集
 */
def 数据集 = 数据集.sort("排序字段1"[, "排序字段2"])[.index("序号字段名")]

/**【用法示例】**/
// 根据id_no降序和age升序,对记录排序
def dataset = dataset.sort("id_no desc", "age asc")
//排序后增加一列排序字段，字段名为rowNum
def dataset = dataset.sort("id_no desc", "age asc").index("rowNum")
```

**distributeSort()**

根据指定字段分区，再按照指定字段排序

```groovy
/**
 * 【语法说明】
 *  分区字段: 支持直接指定字段名称。多个字段时，使用英文逗号连接
 *  排序字段: 选填，支持直接指定字段名称。多个字段时，使用英文逗号连接。
 * 返回值：新数据集
 */ 
def 数据集 = 数据集.distributeSort("分区字段1,分区字段2,..." , "排序字段1,排序字段2,...")

/**【用法示例】**/
def dataset = dataset.distributeSort("id_no, name" , "age")
```

#### 2.2.5 分页算子

**limit()**

限制查询结果条数，用法如下：

```groovy
/**
 * 【语法说明】
*  起始位置: 记录下标,第一条是0
 * 查询条数：要控制返回的记录条数
 * 返回值：新数据集
 */
def 数据集 = 数据集.limit([起始位置,] 查询条数)

/**【用法示例】**/
// top n
def datasetA = dataset.limit(1000)
// paging
def datasetB = dataset.limit(0,50)
```

#### 2.2.6 去重算子

**distinct()**

对数据去重，用法如下：

```groovy
/**
 * 【语法说明】
*  去重字段: 用于判断记录重复的字段,可选,不填则全部字段去重
 * 返回值：新数据集
 */
def 数据集 = 数据集.distinct([去重字段1,去重字段2,...])

/**【用法示例】**/
// 全量字段去重
def datasetA = dataset.distinct()
// 指定字段去重
def datasetB = dataset.distinct("id_no","name")
```

#### 2.2.7 交差并算子

**union()**

**unionAll()**

取两个数据集的并集，用法如下：

```groovy
/**
 * 【语法说明】
 * union 去掉重复的结果
 * unionAll 不会去掉重复的结果
 * 数据集：指定需要进行并集操作的另一个数据集,两个运算的数据集要求结构一致，如果不一致需要用select统一
 * 返回值：返回并集之后的新数据集
 */
def 数据集 = 数据集1.union(数据集2)
def 数据集 = 数据集1.unionAll(数据集2)
/**【用法示例】**/
def datasetA = from(fmdbDs, "stg_per_base").select("id_no","mobile")
def datasetB = from(fmdbDs, "stg_mobile_base").select("id_no","mobile")
def datasetC = datasetA.union(datasetB)
def datasetD = datasetA.unionAll(datasetB)
```

**subtract()**

**subtractAll()**

取两个数据集的差集，用法如下：

```groovy
/**
 * 【语法说明】
 * subtract 去掉重复的结果
 * subtractAll 不会去掉重复的结果
 * 数据集：指定需要进行差集操作的另一个数据集,两个运算的数据集要求结构一致，如果不一致需要用select统一
 * 差集字段：可选，可以指定一个或多个字段取差集
 * 返回值：返回差集操作之后的新数据集
 */
def 数据集 = 数据集1.subtract(数据集2 [,"差集字段"])
def 数据集 = 数据集1.subtractAll(数据集2 [,"差集字段"])
/**【用法示例】**/
def datasetA = from(fmdbDs, 'stg_per_base').select("id_no","mobile")
def datasetB = from(fmdbDs, 'stg_mobile_base').select("id_no","mobile")
def datasetC = datasetA.subtract(datasetB)
def datasetD = datasetA.subtractAll(datasetB)
//或
def datasetC = datasetA.subtract(datasetB, "id_no")
def datasetD = datasetA.subtractAll(datasetB, "id_no")
```

**intersect()**

**intersectAll()**

取两个数据集的交集，用法如下：

```groovy
/**
 * 【语法说明】
 * intersect 去掉重复的结果
 * intersectAll 不会去掉重复的结果
 * 数据集：指定需要进行交集操作的另一个数据集,两个运算的数据集要求结构一致，如果不一致需要用select统一
 * 交集字段：可选，可以指定一个或多个字段取交集
 * 返回值：返回交集操作之后的新数据集
 */
def 数据集 = 数据集1.intersect(数据集2[, "交集字段"])
def 数据集 = 数据集1.intersectAll(数据集2[, "交集字段"])
/**【用法示例】**/
def datasetA = from(fmdbDs, 'stg_per_base').select("id_no","mobile")
def datasetB = from(fmdbDs, 'stg_mobile_base').select("id_no","mobile")
def datasetC = datasetA.intersect(datasetB)
def datasetD = datasetA.intersectAll(datasetB)
//或
def datasetC = datasetA.intersect(datasetB, "id_no")
def datasetD = datasetA.intersectAll(datasetB, "id_no")
```

#### 2.2.8 关联算子

**join()**

两个数据集进行内关联，join()操作默认是内关联，用法如下：

```groovy
/**
 * 【语法说明】
 * 数据集：指定需要进行关联操作的另一个数据集
 * 关联条件：指定两个数据集进行关联的条件，关联条件写法遵循sql规范，关联条件也可以嵌套函数
 * 时间字段：可选参数,当流表进行动态维表join时设置流表的时间列字段
 * 返回值：返回关联操作之后的新数据集
 */
def 数据集 = 数据集1.join(数据集2, "关联条件" [, "时间字段"])
/**【用法示例】**/
def datasetA = from(fmdbDs, 'stg_per_base')
def datasetB = from(fmdbDs, 'stg_mobile_base')
def datasetC = datasetA.join(datasetB, 'stg_per_base.id_no = stg_mobile_base.id_no')
// 动态维表join
def datasetD = datasetA.join(datasetB, 'stg_per_base.id_no = stg_mobile_base.id_no', 'stg_per_base.time')
```

**leftJoin()**

两个数据集进行左关联，用法如下：

```groovy
/**
 * 【语法说明】
 * 数据集：指定需要进行关联操作的另一个数据集
 * 关联条件：指定两个数据集进行关联的条件，关联条件写法遵循sql规范，关联条件也可以嵌套函数
 * 返回值：返回关联操作之后的新数据集
 */
def 数据集 = 数据集1.leftJoin(数据集2, "关联条件")
/**【用法示例】**/
def datasetA = from(fmdb, 'stg_per_base')
def datasetB = from(fmdb, 'stg_mobile_base')
def datasetC = datasetA.leftJoin(datasetB, 'stg_per_base.id_no = stg_mobile_base.id_no')
```

**rightJoin()**

两个数据集进行右关联，用法如下：

```groovy
/**
 * 【语法说明】
 * 数据集：指定需要进行关联操作的另一个数据集
 * 关联条件：指定两个数据集进行关联的条件，关联条件写法遵循sql规范，关联条件也可以嵌套函数
 * 返回值：返回关联操作之后的新数据集
 */
def 数据集 = 数据集1.rightJoin(数据集2, "关联条件")
/**【用法示例】**/
def datasetA = from(fmdb, 'stg_per_base')
def datasetB = from(fmdb, 'stg_mobile_base')
def datasetC = datasetA.rightJoin(datasetB, 'stg_per_base.id_no = stg_mobile_base.id_no')
```

**fullJoin()**

两个数据集进行全关联，用法如下：

```groovy
/**
 * 【语法说明】
 * 数据集：指定需要进行关联操作的另一个数据集
 * 关联条件：指定两个数据集进行关联的条件，关联条件写法遵循sql规范，关联条件也可以嵌套函数
 * 返回值：返回关联操作之后的新数据集
 */
def 数据集 = 数据集1.fullJoin(数据集2, "关联条件")
/**【用法示例】**/
def datasetA = from(fmdb, 'stg_per_base')
def datasetB = from(fmdb, 'stg_mobile_base')
def datasetC = datasetA.fullJoin(datasetB, 'stg_per_base.id_no = stg_mobile_base.id_no')
```

#### 2.2.9 ARK算子

**ark()**

复用ark上的模型算子，用法如下：

```groovy
/**
 * 【语法说明】
 * 服务地址： 指定ark服务的地址https(http)://IP：端口
 * 算子ID: 指定ark算子ID
 * 数据源: ark计算的数据源环境
 * inputParams: 输入参数（无参数时此语法可不写），指定算子输入表map, key->value
 * columnParams：映射字段（无参数时此语法可不写），指定算子的映射字段map, key->value
 * otherParams：其它参数（无参数时此语法可不写），指定算子的其它参数map, key->value
 * outputParams：输出参数（无参数时此语法可不写），指定算子的输出表map
 * 返回值：返回根据ark算子创建的数据集对应的数据源
 * depend：(可选) 需要依赖的算子对象,可以是数据集，可以是类似ark的数据源
 *【注意】ark服务所在库和fmdb数据源定义的所在库不一致时，传参是表名需要带上对应的库名。
 */
def 数据源 = ark(服务地址, 算子ID, 数据源)
    .inputParams("inKey1":"输入表名1", ...)
    .columnParams("colKey1":"映射字段1", ...)
    .otherParams("oKey1":"其它参数1", ...)
    .outputParams("outKey1":"输出表名1", ...)
	[.depend(dataset1[,arkds])]
// 可以省略ark服务地址, 默认会从tre.properties文件中读取tre.ark.address配置项
def 数据源 = ark(算子ID, 数据源)
/**【用法示例】**/
// 作为第一个节点写法无依赖输入，inputParams参数没有时，可不写，语法如下
def dsArk1 = ark("https://127.0.0.1:2024", '30061',ds)
		.columnParams("inTable1":"认证信息")
         .otherParams("key1":"value1","key2":"value2")
         .outputParams("outTable1":"table1","outTable2":"table2")

// 作为中间节点写法，依赖一个df1和另外一个ark算子
def df1 = from(fmdbJS, "t_person")
def dsArk2 = ark("https://127.0.0.1:2024", '30062',ds)
    .inputParams("input1":"输入表名1","inout2":"输入表名2","inout2":"tt输入表名2")
    .columnParams("手机号所在列":"src_auth_account")
    .otherParams("key1":"value1","key2":"value2")
    .outputParams("outTable":"手机号查询归属地1","outTable":"手机号查询归属地2")
    .depend(df1,df2,dsArk1)
```

**fromArk()**

ARK算子后续增加其他基础类操作算子，例如select().where() 等：

```groovy
/**
 * 【语法说明】
 * ARK数据源：表示要提取的ARK数据源名称，用变量引用表示
 * 表名：表示数据源中要提取的表名，用字符串表示
 * alias：表示用于给数据集定义别名，类似sql语法中对表名进行定义别名
 * 返回值：为自定义数据集名称，后续对数据集的引用，可用数据集名称代替 
 */
def 数据集 = fromArk(ARK数据源,"[库名.]表名")[.alias("别名")]

/**【用法示例】**/
def dsArk1 = ark("https://127.0.0.1:2024", '30061',ds)
		.columnParams("inTable1":"认证信息")
         .otherParams("key1":"value1","key2":"value2")
         .outputParams("outTable1":"table1","outTable2":"table2")
def outDf1 = fromArk(dsArk,"table1").select("id","name")
def toDf1 = outDf1.to(ds,"t_table1_new")
def outDf2 = fromArk(dsArk,"table2").select("id","name")
def toDf2 = outDf2.to(ds,"t_table2_new")
```

#### 2.2.10 全文算子

**search()**

用于创建全文检索数据源。

**fromSearch()**

用于创建全文检索数据集，该方法只能传入全文检索类型的数据源，用法如下：

```groovy
/**
 * 【语法说明】
 * 全文地址： 指定全文检索地址
 * 全文端口： 指定全文检索端口
 * 返回值：返回全文数据源
 */
def 数据源 = search("全文地址", 全文端口)
def 漂移数据源 = search()[.areaCode("400100")]
/**
 * 【语法说明】
 * 全文数据源： 指定上一步创建的全文数据源
 * 协议类型： 指定要检索的协议类型
 * 检索起始时间： 检索起始时间，格式为"yyyy-MM-dd HH:mm:ss"
 * 检索结束时间： 检索结束时间，格式为"yyyy-MM-dd HH:mm:ss"
 * 检索条件： 按照全文检索规范书写即可
 * 关键词： 指定要检索的关键词，支持多个关键词以数组形式传入（TRE针对多个关键词用OR组合）
 			如传入["关键词1","关键词2"]，TRE将其组合成("关键词1") OR ("关键词2")
 * 附加字段(可选)： 多个字段逗号隔开
 * 返回值：返回全文数据源
 * depend：(可选) 需要依赖的数据集对象,当全文算子依赖数据集对象时，关键词的值为数据集对象的某个字段名，
            只支持单个字段，不能以数组形式传入
 * 【注意】fromSearch后面必须指定全文结果落地到哪个数据源哪张表
 */
def 数据集 = fromSearch(全文数据源, "协议类型", "检索起始时间", "检索结束时间", "关键词"[, "附加字段"]).to(数据源,"目标表名")[.depend(dataset1)]

/**【用法示例】**/
// 作为第一个节点写法无依赖输入
def fmdbDs1 = fmdb("fmdb-sample")
def searchDs1 = search("192.168.1.18", 9870)
def res1 = fromSearch(searchDs1, 
                         "IM",
                         "2024-04-15 00:00:00", 
                         "2024-04-18 00:00:00", 
                         "a OR b",
                        "附件字段1,附件字段2")
				.to(fmdbDs1,"t_search_temp")
// 作为中间节点写法，依赖一个df1
def fmdbDs2 = fmdb("fmdb-sample")
def df = from(fmdbDs2, "t_person")
def searchDs2 = search("192.168.1.18", 9870)
def res2 = fromSearch(searchDs2, 
                         "IM",
                         "2024-04-15 00:00:00", 
                         "2024-04-18 00:00:00", 
                         "f1",
                        "附件字段1,附件字段2")
				.to(fmdbDs2,"t_search_temp").depend(df)
				
//作为漂移节点写法
def fmdbDs = fmdb("fmdb-sample")
def fmdbDsDrift = fmdb("fmdb-massdata").areaCode("440100")
def searchDsDrift = search().areaCode("440100")
def resDrift = fromSearch(searchDsDrift, 
                         "IM",
                         "2024-04-15 00:00:00", 
                         "2024-04-18 00:00:00", 
                         "a OR b",
                        "附件字段1,附件字段2")
				.to(fmdbDsDrift,"t_search_temp")
def res = resDrift.select("_QUERY_URL,  STRSRC_IP,  _QUERY_CONTENT, UPAREAID, _MAINFILE").to(fmdbDs,"t_search_res")

// 多个关键词写法
def res = fromSearch(searchDs1, 
                         "IM",
                         "2024-04-15 00:00:00", 
                         "2024-04-18 00:00:00", 
                         ["a","b"],
                        "附件字段1,附件字段2")
				.to(fmdbDs1,"t_search_temp")
```

#### 2.2.11 输出算子

将数据输出到指定目标数据源中,支持插入,覆盖更新和fmdb的分区表插入：对于实时算子，在实际输出操作中，tre会自动添加两个字段：`tre_current_time` (bigint) 用于记录数据的入库时间； `tre_current_date` (string) 用于分区表的日期字段。

```groovy
/**
 * 【语法说明】
 * 将数据输出(插入)到数据库
 * 数据集：用数据集变量引用表示
 * 目标表名：输出到指定表中
 * --fields()：部分场景可选属性方法，参数“目标表字段信息”格式："字段名 数据类型 注释"，
 *             输出到流表时要求必传fields，如果表已经存在fields无效。
 * --ttl()：可选属性方法，设置表数据淘汰周期（单位秒），参数secondTime：数值秒
 * --overwrite():可选属性方法，参数为空时，表示将数据覆盖插入到数据库全表
                                       参数不为空时，表示将数据覆盖插入到指定分区
 * --partition():可选属性方法，将源端数据集中的数据按照分区的形式插入目标分区表中,
 *                   参数“分区表达式”：多个分区字段用逗号分隔
 * --upsert():可选属性方法，将源端数据集中的数据upsert到目标表中
 * --view()：可选属性方法，创建视图表，tornadoF数据源不支持此属性方法
 * 返回值：指向目标表的数据集对象
 */
def 数据集 = 数据集.to(数据源,"目标表名"[,"目标表字段信息"])
/**【用法示例】**/
def dataset = dataset.to(mysqlDs,"t_user")
					[.fields("目标表字段信息")]
					[.ttl(secondTime)]
					[.overwrite()]
					[.partition("分区表达式")]
					[.upsert()]
                    [.view()]
//------1.将数据输出(插入)到数据库-------------------------------------------
def dataset = dataset.to(mysqlDs,"t_user")
//------2.将数据输出(插入)到数据库，如果表不存在按照指定的表结构创建------------
def dataset = dataset.to(mysqlDs,"t_user").fields("id int,name string comment '姓名'");
//------【废弃写法】通过参数传递的字段的写法不推荐，1.3版本之后会逐步废弃--------
def dataset = dataset.to(mysqlDs,"t_user","id int,name string comment '姓名'");
//------3.将数据输出(插入)到数据库，并设置表数据淘沙周期为1天（86400秒）------
def dataset = dataset.to(fmdbDs,"t_user").ttl(86400)
//------4.将数据覆盖插入到数据库或非分区表---------------------------------------------
def dataset = dataset.to(fmdbDs,"t_user").overwrite()
//------5.orc分区表：将源端数据集中的数据按照分区的形式插入目标分区表中--------------------
def dataset = dataset.to(fmdbDs,"t_user").partition("p1 = 'final', p2 = 'update', p3 = '1729755161', p4 = '20241024'")
//------6.orc分区表：清空全表分区，并将数据覆盖插入到指定分区---------------------------------------------
def dataset = dataset.to(fmdbDs,"t_user").overwrite().partition("p1 = 'final', p2 = 'update', p3 = '1729755161', p4 = '20241024'")
//------7.orc分区表：清空指定分区，并将数据覆盖插入到指定分区---------------------------------------------
def dataset = dataset.to(fmdbDs,"t_user").overwrite("partition").partition("p1 = 'final', p2 = 'update', p3 = '1729755161', p4 = '20241024'")
//------8.frc分区表：清空全表分区，并将数据覆盖插入到指定分区---------------------------------------------
def dataset = dataset.to(fmdbDs,"t_user").overwrite().partition("partition_name='20250719'")
//------9.frc分区表：清空指定分区，并将数据覆盖插入到指定分区---------------------------------------------
def dataset = dataset.to(fmdbDs,"t_user").overwrite("partition").partition("partition_name='20250719'")
//------10.将源端数据集中的数据upsert到目标表中--------------------------------
def dataset = dataset.to(fmdbDs,"t_user").upsert()
//------11.将源端数据集中的数据输出到视图表中--------------------------------
def dataset = dataset.to(fmdbDs,"t_user").view()
//------12.将源端数据集中的数据输出到视图表中，并设置视图表字段类型--------------------------------
def dataset = dataset.to(fmdbDs,"t_user").fields("id int,name string comment '姓名'").view()
```

#### 2.2.12 SQL算子

数据库原生SQL查询，用法如下：

```groovy
/**
 * 【语法说明】
 * 数据源：要执行SQL的数据库对象
 * 查询SQL：符合数据库语法要求的sql查询语句
 * 返回值：为自定义数据集名称，后续对数据集的引用，可用数据集变量代替
 * depend：(可选) 需要依赖的算子对象,可以是数据集，可以是类似ark的数据源；表示等待前序步骤执行完成
 */
// 自定义算子
def 数据集 = query(数据源,"查询SQL")[.depend(dataset1[,arkds])]

/**【用法示例】**/
// 无依赖
def dataset = query(mysqlDs,"select * from t_user where user_id='xxx'")
// 有依赖
def node_4_noalias = node_3.to(mysqlDs, "t_test").nodeId("4")
def dataset = query(mysqlDs,"select * from t_test ").depend(node_4_noalias)
// 有依赖,但是前置结果不想主动落库的情况,可以借助算子的临时表名来写查询SQL
def df1 = from(pgDs,"t_test").select("f1,func(f2) as f2,f3")
def df2 = query(pgDs,"select * from ${df1.tempTable}").depend(df1)

```

数据库原生插入算子，用法如下：

```groovy
/**
 * 【语法说明】
 * 数据源：要执行插入SQL的数据库对象
 * 目标表：执行插入SQL的目标表，用于作为后续步骤的表名
 * 插入SQL: 符合数据库语法要求的insert语句，目前只支持insert、upsert开头的语句
 * 返回值: 为自定义数据集名称，后续对数据集的引用，可用数据集变量代替
 * depend: (可选) 需要依赖的算子对象,可以是数据集，可以是类似ark的数据源，表示等待前序步骤执行完成
 */
// 自定义算子
def 数据集 = insert(数据源, "目标表", "插入SQL")[.depend(df[, arkds])]

/**【用法示例】**/
// 无依赖
def df0 = insert(mysqlDs, "tre_test_0001", "insert into my_table(insert_date) values('2025-04-03')")
// 有依赖
def df1 = query(mysqlDs, "select * from t_test").to(mysqlDs, 'tre_temp_test')
def df2 = insert(mysqlDs, "tre_test_0001", "insert into tre_test_0001(insert_date) values('2025-04-03')").depend(df1)


```

#### 2.2.13 返回算子

用于将计算的结果集返回给调用客户端，用法如下：

```groovy
/**
 * 【语法说明】
 * 数据集：需要返回的数据集，用数据集变量引用表示
 * 默认返回100条数据
 */
returnDf(数据集)

/**【用法示例】**/
def pgDs = postgres('172.16.31.206', 15432, 'user', 'password', 'dbName')
def dataset = query(pgDs, "select * from t_user where user_id='xxx'")
returnDf(dataset)
```

#### 2.2.14 扩展算子

用户自定义扩展算子，用法如下：

```groovy
/**
 * 【语法说明】
 * 数据集名称：用数据集变量引用表示
 * commandName：用户扩展插件注册的自定义算子函数名称
 * 参数列表: 用户扩展插件里自定义算子函数定义的参数
 * 返回值：为自定义数据集名称，后续对数据集的引用，可用数据集变量代替 
 */
def 数据集名称 = 数据集名称.commandName(参数列表)

/**【用法示例】**/
// 给name字段添加"_fh"后缀,
def dataset = dataset.addSuffix("name","_fh")
```

```java
/**
 * 自定义处理算子实现样例
 */
public class MyProcess extends CustomProcess {
    private String fieldName;
    private String suffix;

    /**
     * 自定义算子命令方法,会在语法层镜像出一个相同的命令
     */
    @CommandMethod
    public void addSuffix(String fieldName, String suffix) {
        this.fieldName = fieldName;
        this.suffix = suffix;
    }

    /**
     * 处理数据
     */
    @Override
    public DataFrame process(DataFrame df) {
        for (DataFrame.Row row : df) {
            row.setValue(fieldName,row.getValue(fieldName)+suffix);
        }
        return df;
    }
}
```

#### 2.2.15  exists算子

**exists()**

用于检查子查询是否至少返回一行数据

```groovy
/**
 * 【语法说明】
  * 数据集：指定需要进行查询数据是否存在的另一个数据集
  * 关联条件: 可选, 指定两个数据集进行关联的条件，关联条件写法遵循sql规范
  * 返回值：返回左边数据集在右边数据集中存在的数据
 */
def 数据集 = 数据集.exists(数据集2, "关联条件")
/**【用法示例】**/
def datasetA = from(fmdbDs, 'stg_per_base')
def datasetB = from(fmdbDs, 'stg_mobile_base')
def dataset = datasetA.exists(datasetB, "stg_per_base.id_no = stg_mobile_base.id_no")
```

**notExists()**

notExists是与exists相反的条件语句，用于检查子查询中是否没有返回任何数据

```groovy
/**
 * 【语法说明】
  * 数据集：指定需要进行查询数据是否存在的另一个数据集
  * 关联条件: 可选, 指定两个数据集进行关联的条件，关联条件写法遵循sql规范
  * 返回值：返回左边数据集在右边数据集中不存在的数据
 */
def 数据集 = 数据集.notExists(数据集2, "关联条件")
/**【用法示例】**/
def datasetA = from(fmdbDs, 'stg_per_base')
def datasetB = from(fmdbDs, 'stg_mobile_base')
def dataset = datasetA.notExists(datasetB, "stg_per_base.id_no = stg_mobile_base.id_no")
```

#### 2.2.16 分组排序取第一条算子

**groupSortFirst()**

根据指定的分组字段和排序字段 分组排序后输出每组的第一条数据

```groovy
/**
 * 【语法说明】
 *  分组字段: 支持直接指定字段名称。多个字段时，使用英文逗号连接
 *  排序字段: 字段名 asc|desc。  多个字段时，使用英文逗号连接
 * 返回值：新数据集
 */ 
def 数据集 = 数据集.groupSortFirst("分组字段1,分组字段2,..." , "排序字段1,排序字段2,...")

/**【用法示例】**/
// 去重字段和排序字段
def dataset = dataset.groupSortFirst("id_no, name" , "age desc")
```

#### 2.2.17 注释算子

**comment()**

用于记录tsml脚本的设计思路说明，TRE引擎会收集这些注释和TSML的对应关系

```groovy
/**
 * 【语法说明】
 *  注释内容: TSML脚本相关的注释内容
 * 返回值：无
 */
comment("""注释内容""")

/**【用法示例】**/
comment("""模型构建思路相关注释""")
```

#### 2.2.18 http算子

**http()**

用于发送http请求，并获取响应结果

```groovy
/**
 * 【语法说明】
 * 请求方式： 必填，指定http请求方式，如GET POST
 * 服务url: 必填，指定http服务地址，如http://127.0.0.1:8080/test/list
 * connectionTimeout: int,选填，设置连接超时（毫秒），默认5000
 * readTimeout: int,选填，设置读写超时（毫秒），默认5000
 * header: 选填，请求头, Map key->value
 * form：POST请求下发送普通表单，GET请求下发送查询参数，Map key->value
 * body：发送 POST JSON请求，JSON字符串; POST请求下form和body二选一
 * page：选填，分页信息
 		pageType：1：自然页码(1,2,3,...)  2：偏移量(0,10,20,...)
 		pageStart：页码起始值，当页码方式选择自然页码时，该值表示第N页(1--第1页)； 当选择偏移量时，该值表示从第N+1条数据开始(0--第1条，10--第11条)
 		pageSize：每页数据量，默认值100
 		maxPage：最大分页数，可以限制最大分页数，0 -- 表示不限制，直至数据查询不到
 		dataPath：填写分页数据所在的Data-Path路径，用于检测分页是否结束
 		
 		TRE通过内置变量 ${VAR_HTTP_PAGE_NO}、${VAR_HTTP_PAGE_OFFSET}、${VAR_HTTP_PAGE_SIZE}来表示分页信息，
        你可以在需要的位置为 参数值指定上述变量 (例如：page=${VAR_HTTP_PAGE_NO}) 来进行分页信息传递。
        ${VAR_HTTP_PAGE_NO} -- 表示 自然页码 (1, 2, 3, ...)
        ${VAR_HTTP_PAGE_OFFSET} -- 表示 偏移量式页码 (0, 10, 20, ...)
        ${VAR_HTTP_PAGE_SIZE} -- 表示 每页数据量大小
 * response：选填，提取响应内容
 		dataPath：数据Data-Path
 		arrayFlat：是否数组展平，布尔型，true展平，false不展平
 		columns：Map结构 提取的每个字段名称fieldName、字段path路径fieldPath、数据类型dataType、长度dataLength、字段注释comment
 		数据类型dataType说明：VARCHAR,INTEGER,BIGINT,DECIMAL,DATE,TIMESTAMP等
 		不填时表示将响应体body作为一个字段入到目标表，字段名称默认为response
 * auth：选填，依赖的认证http算子，如auth(df)		
 		
 * http请求参数可以从前面的数据集中提取，通过REF{字段名称}配置
 */
def 数据集 = http(请求方式, 服务url).areaCode("440100")       
	.connectionTimeout(3000)  
    .readTimeout(10000)    
    .header("key1":"value1", ...)
    .form("username":"john", ...)
    .body("{\"name\":\"Alice\",\"age\":30...}")
	.page(1, 1, 20, 0, "/data")
	.response("/data", true, [["fieldName":"name","fieldPath":"/name","dataType":"VARCHAR","dataLength":512,"comment":"姓名"],...])
	.to(fmdbDs,"http_result_20250521")

/**【用法示例】**/
// 作为第一个节点写法 无依赖输入  get带查询参数
def fmdbDs = fmdb()
def df = http("GET", "http://10.0.9.173:8083/nacos/test/get")
			.connectionTimeout(4000)
			.readTimeout(6000)
			.form("taskId":"40003f293c11455a95f2c18ead7987d1")
         	.response("/data", true,[["fieldName":"taskId","fieldPath":"/taskId","dataType":"VARCHAR","dataLength":50,"comment":"任务id"]])
			.overwriteTo(fmdbDs,"tre.http_result_2025052600000")

// 作为第一个节点写法 无依赖输入   post表单
def fmdbDs = fmdb()
def df = http("POST", "http://10.0.9.173:8083/nacos/test/form")
			.connectionTimeout(4000)
			.readTimeout(6000)
			.form("taskId":"asdas21312300","time":1212100)
			.response("/data", true,[["fieldName":"taskId","fieldPath":"/taskId","dataType":"VARCHAR","dataLength":50,"comment":"任务id"],["fieldName":"time","fieldPath":"/time","dataType":"INTEGER","dataLength":8,"comment":"时间"]])
			.overwriteTo(fmdbDs,"tre.http_result_2025052600322")

// 作为中间节点写法，依赖一个df数据集  post json
def fmdbDs = fmdb()
// 查询出要处理的数据
def df = from(fmdbDs,"http_model")
def df2 = df.http("POST", "http://172.17.14.151:8070/tre/execute")
			.connectionTimeout(5000) 
			.readTimeout(10000)      
			.body("""{"code":"def tf = tornadoF()","taskId":"REF{taskid}"}""")
         	.response("/data", true,[["fieldName":"taskId","fieldPath":"/taskId","dataType":"VARCHAR","dataLength":50,"comment":"任务id"]])
			.to(fmdbDs,"tre.http_result_20250521")

// url中携带动态参数，参数来源于一个df数据集
def fmdbDs = fmdb()
// 查询出要处理的数据
def df = from(fmdbDs, "tre.t_1023_51231")
def df2 = df.http("GET", "http://10.0.9.173:8083/nacos/test/get/REF{name}")
			.connectionTimeout(5000) 
			.readTimeout(10000)
         	.response("/data", true,[["fieldName":"taskId","fieldPath":"/taskId","dataType":"VARCHAR","dataLength":50,"comment":"任务id"]])
			.to(fmdbDs,"tre.http_result_20250521")
                  
//  post 分页查询
def fmdbDs = fmdb()
def df = http("POST", "http://172.16.42.142/job/queryForPage")
			.connectionTimeout(5000) 
			.readTimeout(10000)  
			.body("""{"pageItems":${VAR_HTTP_PAGE_SIZE},"pageNo":${VAR_HTTP_PAGE_NO},"condition":{}}""")
			.page(1, 1, 20, 0, "/data")
         	.response("/data", true,[["fieldName":"nodeId","fieldPath":"/nodeId","dataType":"VARCHAR","dataLength":50,"comment":"节点id"]])
			.to(fmdbDs,"tre.http_result_20250521")

//  http依赖场景 如：先获取job列表，再根据列表数据获取任务列表
def fmdbDs = fmdb()
def df = http("POST", "http://172.16.42.142/job/queryForPage")
			.connectionTimeout(5000) 
			.readTimeout(10000)  
			.body("""{"pageItems":${VAR_HTTP_PAGE_SIZE},"pageNo":${VAR_HTTP_PAGE_NO},"condition":{}}""")
			.page(1, 1, 20, 2, "/data")
         	.response("/data", true,[["fieldName":"jobId","fieldPath":"/jobId","dataType":"VARCHAR","dataLength":50,"comment":"任务id"]])
			.overwriteTo(fmdbDs,"tre.http_result_20250527xx1")
def df2 = df.http("GET", "http://172.16.42.142/task/queryList")
			.connectionTimeout(5000) 
			.readTimeout(10000)  
			.form("jobId":"REF{jobId}")
         	.response("/data", true,[["fieldName":"jobName","fieldPath":"/jobName","dataType":"VARCHAR","dataLength":50,"comment":"任务名称"],["fieldName":"jobCreateTime","fieldPath":"/jobCreateTime","dataType":"BIGINT","dataLength":18,"comment":"任务创建时间"]])
			.to(fmdbDs,"tre.http_result_20250527xx2")

// 漂移场景  http服务在异地
def fmdbDs = fmdb()
def fmdbDs2 = fmdb("fmdb20D").areaCode("441200")
def df = from(fmdbDs, "tre.base_attribute_info").where("parent_attribute_type = 1002")
def df2 = df.http("GET", "http://10.0.9.173:8083/nacos/test/get").areaCode("441200")
def df3 = df2.connectionTimeout(4000)
			.readTimeout(6000)
			.form("taskId":"REF{ename}")
         	.response("/data", true,[["fieldName":"taskId","fieldPath":"/taskId","dataType":"VARCHAR","dataLength":50,"comment":"任务id"]])
			.to(fmdbDs2,"tre.http_result_202505270000001112")
def df4 = df3.to(fmdbDs,"tre.http_result_202505270000002221")

// 先获取认证token  再携带token访问数据接口
def fmdbDs = fmdb()
def df = http("POST", "http://gdk.njsecnet.com/mockapi/getToken")
			.connectionTimeout(5000)
			.readTimeout(10000)
			.form("sid":"tre","secretKey":"MTIzMjNzZGFzZGFzZA==")
         	.response("/data", true,[["fieldName":"token","fieldPath":"/token","dataType":"VARCHAR","dataLength":48]])
def df2 = http("POST", "http://172.16.31.205:8060/job/queryForPage")
			.auth(df)
			.connectionTimeout(5000)
			.readTimeout(10000)
			.header("token":"REF{token}")
			.body("""{"pageItems":${VAR_HTTP_PAGE_SIZE},"pageNo":${VAR_HTTP_PAGE_NO},"sortName":"createTime","sortOrder":"desc","draw":3,"condition":{}}""")
            .page(1, 1, 20, 1, "/data/data")
            .response("/data/data", true,[["fieldName":"jobId","fieldPath":"/jobId","dataType":"VARCHAR","dataLength":50,"comment":"任务id"],["fieldName":"jobName","fieldPath":"/jobName","dataType":"VARCHAR","dataLength":500,"comment":"任务名称"],["fieldName":"sourceConnectionType","fieldPath":"/sourceConnectionType","dataType":"VARCHAR","dataLength":50,"comment":"源端数据源类型"]])
            .to(fmdbDs,"tre.http_result_20250526003144660")

// OAuth2 认证场景
def fmdbDs = fmdb()
def df = http("POST", "https://15.6.138.101:8443/xsp-auth/oauth2/token")
			.connectionTimeout(5000)
			.readTimeout(10000)
			.body("""{"client_id":"abc","client_secret":"dfg123","grant_type":"client_credentials","scope":"email"}""")
         	.response("/", true,[["fieldName":"access_token","fieldPath":"/access_token","dataType":"VARCHAR","dataLength":48],["fieldName":"expires_in","fieldPath":"/expires_in","dataType":"VARCHAR","dataLength":10]])// 此处必须添加expires_in字段
def df1 = from(fmdbDs, "tre.base_attribute_info").where("parent_attribute_type  = 1015 and attribute_type > 35527 ").select("name, code, mobile")
def df2 = df1.http("POST", "http://15.6.138.101:8999/api/res/R-010000000000-30004462")
	        .auth(df)
			.connectionTimeout(5000)
			.readTimeout(10000)
			.header("Authorization":"Bearer REF{access_token}","xsp-user":"用户信息域base64编码") // access_token来源于认证http算子
			.body("""{"params": {
                            "mobile": "REF{mobile}"   // mobile来源于依赖表输入
                        },
                        "page": {
                            "skip": ${VAR_HTTP_PAGE_OFFSET},
                            "limit": ${VAR_HTTP_PAGE_SIZE}
                        },
                        "config": {
                            "schema": true,
                            "data": true
                        }}""")
            .page(2, 0, 10, 0, "/data")
            .response("/data", true,[["fieldName":"B050022","fieldPath":"/B050022","dataType":"VARCHAR","dataLength":50,"comment":"发送方用户ID"],["fieldName":"K000117","fieldPath":"/K000117","dataType":"VARCHAR","dataLength":500,"comment":"终端IP地址归属地行政区划"],["fieldName":"B030811","fieldPath":"/B030811","dataType":"VARCHAR","dataLength":50,"comment":"发送方IP归属地"]])
            .to(fmdbDs,"tre.http_result_20250526003144660")
```

#### 2.2.19 python算子

**python()**

用于执行python脚本，并获取脚本执行结果

```groovy
/**
 * 【语法说明】
 *  数据源: python脚本计算结果数据集对应的数据源
 *  输出表名: python脚本计算结果数据集的表名
 *  输出表名: python脚本内容
 * 返回值：新数据集
 */ 
def 数据集 = python(数据源,输出表名，python脚本)

/**【用法示例】**/
def fmdbDs = fmdb()
def dataset = python(fmdbDs, "tre.result_table", "\ndef main()->pd.DataFrame:\n    # 加载数据\n    input_df = load_table('tre.test_python_input_t1', limit=100)\n    # 对 DataFrame 进行操作\n    # 例如：添加新列\n    input_df['C'] = input_df['A'] + input_df['B']\n   output_df = input_df\n    return output_df\n")
```

#### 2.2.20 groovy算子

**groovy()**

用于执行groovy脚本，并获取脚本执行结果

```groovy
/**
 * 【语法说明】
 *  df: groovy脚本的输入数据集
 *  groovy脚本闭包: groovy处理代码，这是一个有输入和输出参数的闭包，输入和输出参数都是dataFrame结构（包括字段和数据）
 *  返回值：groovy脚本处理结果数据集
 *  限制：调用groovy脚本处理后的数据集如果需要，则后面接输出算子；不需要则返回null即可
 */ 

//场景1：groovy脚本依赖输入数据集
def 数据集2 = 数据集.groovy(groovy脚本闭包).to(dataBaseDs,result_table)
//场景2：groovy脚本没有依赖输入
def 数据集 = groovy(groovy脚本闭包).to(dataBaseDs,result_table)

/**【用法示例1】修改字段值，不改变数据集字段结构**/
def fmdbDs = fmdb()
def dataset = from(fmdbDs, "tre.stg_per_base").where(" name = '李四' ").select("id, id_no, name, age, mobile").nodeId("1")
def df2 = dataset.groovy(dataFrame  ->  {
    dataFrame.forEach { row -> {
            String idno = row.getValue("id_no")
            String name = row.getValue("name")
            row.setValue("name", idno+":"+name)
       }
    }
    return dataFrame
}).to(fmdbDs,"tre.result_table0825001")

/**【用法示例2】自定义解析提取http响应结果，构造返回新的数据集字段结构**/
def fmdbDs = fmdb()
def df1 = http("GET", "http://gdk.njsecnet.com/mockapi/mocktest/leftjoinpath")
			.connectionTimeout(4000)
			.readTimeout(6000)
			.to(fmdbDs,"tre.result_table0826001").overwrite()
def df2 = df1.groovy(dataFrame  ->  {
    List<ColumnInfo> columnInfos = new ArrayList<>();
    ColumnInfo idCol = new ColumnInfo();
    idCol.setColumnName("id");
    idCol.setDataTypeName("VARCHAR");
    idCol.setColumnLength(50);
    columnInfos.add(idCol);
    ColumnInfo nameCol = new ColumnInfo();
    nameCol.setColumnName("name");
    nameCol.setDataTypeName("VARCHAR");
    nameCol.setColumnLength(255);
    columnInfos.add(nameCol);
    ColumnInfo codeCol = new ColumnInfo();
    codeCol.setColumnName("code");
    codeCol.setDataTypeName("VARCHAR");
    codeCol.setColumnLength(512);
    columnInfos.add(codeCol);
    RowDataFrame newRowDataFrame = new RowDataFrame(columnInfos);
    dataFrame.forEach { row -> {
            String response = row.getValue("response")
            JSONObject obj = JSON.parseObject(response);
            JSONArray array = obj.getJSONObject("data").getJSONArray("data");
            Iterator it = array.iterator();
            while (it.hasNext()) {
                List<Object> dataList = new ArrayList<>();
                JSONObject jsonObject = (JSONObject)it.next();
                dataList.add(jsonObject.get("id"));
                dataList.add(jsonObject.get("name"));
                dataList.add(jsonObject.get("code"));
                newRowDataFrame.addRowValue(dataList);
            }
       }
    }
    return newRowDataFrame
}).to(fmdbDs,"result_table0826002").overwrite()

/**【用法示例3】groovy脚本没有依赖输入**/
def fmdbDs = fmdb()
groovy(dataFrame  ->  {
    List<ColumnInfo> columnInfos = new ArrayList<>();
    ColumnInfo idCol = new ColumnInfo();
    idCol.setColumnName("id");
    idCol.setDataTypeName("VARCHAR");
    idCol.setColumnLength(55);
    columnInfos.add(idCol);
    RowDataFrame newRowDataFrame = new RowDataFrame(columnInfos);
    // 发http请求，提取响应，解析
    def url = new URL("http://gdk.njsecnet.com/mockapi/mocktest/leftjoinpath")
    def conn = (HttpURLConnection)url.openConnection()
    conn.setRequestMethod("GET")
    def respBody = conn.inputStream.withReader{ it.readLines().join('\n')}
    JSONObject obj = JSON.parseObject(respBody);
    JSONArray array = obj.getJSONObject("data").getJSONArray("data");
    Iterator it = array.iterator();
    while (it.hasNext()) {
        List<Object> dataList = new ArrayList<>();
        JSONObject jsonObject = (JSONObject)it.next();
        dataList.add(jsonObject.get("id"));
        newRowDataFrame.addRowValue(dataList);
    }
    return newRowDataFrame
}).overwriteTo(fmdbDs,"tre.test_table0902xx3")

/**【用法示例4】groovy脚本执行后无需返回结果**/
groovy(dataFrame  ->  {
    // 发http请求，提交表单，无需返回数据
    def url = new URL("http://10.0.9.173:8083/nacos/test/form")
    def conn = (HttpURLConnection)url.openConnection()
    conn.setRequestMethod("POST")
    conn.setDoOutput(true)
    conn.setRequestProperty("Content-Type","application/x-www-form-urlencoded")
    def formData = "name=zhangsan&age=18"
    conn.getOutputStream().withWriter{writer -> writer.write(formData)}
    httpConn.inputStream.withReader {it.readLines().join("\n")}
    conn.disconnect()
    return null
})

/**【用法示例5】groovy脚本异地执行 漂移场景**/
def fmdb_local = fmdb("fmdb20D")
def fmdb_drift = fmdb("210-tre").areaCode("441200")
def df1 = from(fmdb_drift,"tre.stg_per_base").where(" name = '李四' ")
def df2 = df1.groovy({dataFrame  ->  {
    dataFrame.forEach { row -> {
            String idno = row.getValue("id_no")
            String name = row.getValue("name")
            row.setValue("name", idno+":"+name)
       }
    }
    return dataFrame
}}).to(fmdb_drift,"tre.test_table0902yd1")
df2.select("id_no, name").overwriteTo(fmdb_local, "tre.test_table0902yd111")
```

### 2.3 漂移算子语法场景

漂移算子语法对外部TSML使用方不感知，针对含有外部资源的计算逻辑，TRE引擎在优化层，会根据含有不同地区来源的算子（参见2.1.1 areaCode定义），进行子图切割，切割后分为本地算子图和远端执行算子图。

查询异地000003的资源漂移到本地的场景TSML样例

```groovy
def fmdb_000003 = fmdb("fmdb20E").areaCode("000003")
def fmdb_local = fmdb("210-tre")

// 节点名称：江苏省南京市-手机号卡求准表
def node_1 = from(fmdb_000003, "massdata.ADM_MCARD_BASE_INFO").alias("node_1")
// 节点名称：提取
def node_2 = node_1.where("(node_1.UPDATE_TIME <= 1731293680  ) ")
        .select("node_1.MCARD_ID as F11,node_1.MOBILE as F12,node_1.IMSI as F13,node_1.CRED as F14,node_1.MERGE_STRATEGY as F15,node_1.DETAIL as F16,node_1.FIRST_TIME as F17,node_1.LAST_TIME as F18,node_1.VALID_FLAG as F19,node_1.UPDATE_TIME as F20")

// 节点名称：提取-1
node_2.to(fmdb_local, "massdata.PHY_ADM_VMODEL_MID11322_TId12220_NId3_27OFRcBI").nodeId("3")
```

TRE对上述TSML进行切割后分为本地执行TSML和异地执行TSML

【异地执行TSML】

```groovy
// Tre1.2.1于2025-02-26 11:01:14.652自动生成的代码
def fmdbDs1 = fmdb('fmdb20E')
def fromDf3 = from(fmdbDs1, "massdata.ADM_MCARD_BASE_INFO").nodeId("3")
def aliasDf4 = fromDf3.alias("node_1").nodeId("3")
def whereDf5 = aliasDf4.where("(node_1.UPDATE_TIME <= 1731293680  ) ").nodeId("3")
def selectDf6 = whereDf5.select("node_1.MCARD_ID as F11,node_1.MOBILE as F12,node_1.IMSI as F13,node_1.CRED as F14,node_1.MERGE_STRATEGY as F15,node_1.DETAIL as F16,node_1.FIRST_TIME as F17,node_1.LAST_TIME as F18,node_1.VALID_FLAG as F19,node_1.UPDATE_TIME as F20").nodeId("3")
def driftToDf8 = driftTo("000004").attach(selectDf6, "", "tre_temp_d5ecada105664fc6b907e78c152ea126_1740538874", "frc").nodeId("3")
```

【本地执行TSML】

```groovy
def fmdb_local = fmdb("210-tre")
def driftFromDf1 = driftFrom(fmdb_local, "tre_temp_d5ecada105664fc6b907e78c152ea126_1740538874","frc")
def aliasDf2 = driftFromDf1.alias("node_1").nodeId("3")
aliasDf2.to(fmdb_local, "massdata.PHY_ADM_VMODEL_MID11322_TId12220_NId3_27OFRcBI").nodeId("3")
```

**语法说明**

**driftTo()** 

漂移数据源，用于给地市发送漂移邮件

```groovy
/**
 * 【语法说明】
 * areaCode：漂移的地市
 * attach(): 数据集：指定漂移的数据集，原始表名，新表名，类型：枚举值，table、bit、frc
 * 返回值：漂移数据源
 */
def 漂移数据源 = driftTo("areaCode").attach(数据集1, oldTableName,newTableName,"类型")[.attach(数据集2, "类型")]
/**【用法示例】**/
def a1 = from(fmdbJsst, "t_person").alias("a1").nodeId("提取-001")
def a2 = from(fmdbJsst, "t_person2").alias("a2").nodeId("提取-002")
def ds = driftTo("320100").attach(a1,"t_person1","temp_t_person1","table").attach(a1,"t_person2","temp_t_person2","frc")

```

**driftFrom()**(支持等待逻辑，等待数据集的到来，脚本和数据分开走的情况)

如果有数据集漂移过来，使用此命令加载邮件中的数据集

```groovy
/**
 * 【语法说明】
 * 数据源：数据源名称，用变量引用表示
 * 数据名称：返回邮件中指定的数据集，数据集名称对应为附件XXX1.table或者XXX2.bit对应的文件名
 * 返回值：数据集
 */
def 数据集 = driftFrom(ds, "数据集名称","类型")
/**【用法示例】**/
def a1 = driftFrom(fmdbJsst,"a1","frc")
```

### 2.4 实时算子语法场景

#### 2.4.1 数据源定义

**tornadoF()**

```groovy
/**
 * 【语法说明】
 * tornadoF数据源定义 tornadoF(["tfDsConfName","fmdbDsConfName"]), tre1.2.x版本对接了tornadoF
 * tfDsConfName：tornadoF数据源配置目录名称,
 *   数据源配置信息和认证文件统一放在resource/tornadoF/{tfDsConfName}目录中,
 *   数据源配置需要提供swg连接串配置文件
 * fmdbDsConfName：与tornadoF配套的fmdb数据源数据源配置目录名称,
 *   数据源配置信息和认证文件统一放在resource/fmdb/{fmdbDsConfName}目录中
 * tornadoF和fmdb数据源配置需要配对；不填时使用resource/tornadoF/default目录中的tornadoF数据源和resource/fmdb/default目录中的fmdb数据源,指令为tornadoF(),等效tornadoF("default","default")指令
 * 返回值：为自定义数据源的名称，后续对数据源的引用，可用数据源名称代替 
 * 注：使用tornadoF数据源时，字段大小写敏感
 */
def 数据源 = tornadoF(["tfDsConfName","fmdbDsConfName"])

/**【用法示例】**/
// 本地tornadoF数据源定义，fmdb和tf均采用default默认
def tfDefaultDs = tornadoF()
// 指定配置名称的tornadoF数据源定义
def tfSpecifiedDs = tornadoF("default","fmdb-sample")
```

**终端表按照指定业务字段分组归并聚合+离线分析场景TSML样例**

```groovy
// 定义Tornadof数据源信息 
def tf = tornadoF("default", "fmdb-sample")
// 节点名称：终端表数据
def node_4 = from(tornadoF, "odbc.NB_MASS_RESOURCE_AUTH").alias("node_4").nodeId("4")
// 节点名称：终端表提取
def node_5 = node_4.tumbleWindow("PROCTIME", "5", "MINUTES")
                    .where("DATA_SOURCE in ('124','802','111','144')")
                    .group("""
                            window_start, window_end
                            ,IMSI,IMEI,TERMINAL_TYPE
                            """
                            ,"""
                            min(CAPTURE_TIME) as FIRST_TIME, max(CAPTURE_TIME) as LAST_TIME,count(1) AS CCOUNT""")
                    .select("""IMSI,IMEI,TERMINAL_TYPE,
                            FIRST_TIME,
                            LAST_TIME,
                            CCOUNT,
                            'final' as p1,
                            'update' as p2,
                            cast (UNIX_TIMESTAMP(CAST(CURRENT_DATE AS STRING),'yyyy-MM-dd') as string) as p3,
                            DATE_FORMAT(CURRENT_TIMESTAMP, 'yyyyMMdd') as p4
                        """).alias("node_5").nodeId("5")
// 节点名称：终端表提取-1 结果表为分区表，已由bdos提前创建好
def node_6 = node_5.to(tornadoF, "massdata.dwd_res_res_log_netdev_rt_sink").nodeId("6")
// 由于实时任务是不间断往结果表输出数据的，后面接周期性算子执行离线分析治理任务
def node_7 = node_6.periodReactor("0 0 3 * * ?").nodeId("7")
// 2.1 BDOS专用分区抽取
def datasourceDf = fmdb("fmdb-sample")
def node_8 = variable("BdosPartitionIncrementVar" ,[tableName:"massdata.dwd_res_res_log_netdev_rt_sink", partitionName:"p3", stepLength:10, partitionBeginVar:"NODE_6_p3_start", partitionEndVar:"NODE_6_p3_end"])
def node_9 = from(datasourceDf, "massdata.dwd_res_res_log_netdev_rt_sink").depend(node_7).where("p3>='${node_8.NODE_6_p3_start}' and p3<'${node_8.NODE_6_p3_end}' and p1 = 'final' and p2 = 'update'").nodeId("9")
// 提取计算等
def node_10 = node_9.select("IMSI, IMEI, TERMINAL_TYPE, FIRST_TIME, LAST_TIME, CCOUNT").alias("node_10").nodeId("10")
// 输出
def node_11 = node_10.to(fmdb_tre, "massdata.phy_adm_vmodel_mid12364_tid25558_nid42_890sppqcn").nodeId("11")
```

#### 2.4.2 过滤算子

语法参见2.2.1 过滤算子

#### 2.4.3 维表join算子

语法参见2.2.8 关联算子

#### 2.4.4 滚动窗口算子

**tumbleWindow()**

滚动窗口算子，滚动窗口将每个元素指定给指定窗口大小的窗口；滚动窗口具有固定大小，且不重叠。
用法如下：

```groovy
/**
 * 【语法说明】
 * 时间字段：列名，表示该列数据映射到滚动窗口。
 * 窗口大小：数字，指定滚动窗口的窗口大小
 * 窗口时间单位：支持SECONDS，MINUTES，HOURS，DAYS
 * 偏移量大小：可选参数，用于指定窗口开始移动的offset，也就是指定产生窗口的时间点。
 * 偏移量时间单位：可选参数，用于指定窗口偏移量时间单位。
 * 返回值：返回聚合操作之后的新数据集
 * 注：输入数据集要求为流表，窗口函数应该与聚合函数一起使用
 * 分组字段：分组字段只能为window_start和window_end
 */
def 数据集 = 数据集.tumbleWindow("时间字段", "窗口大小", "窗口时间单位" [,"偏移量大小","偏移量时间单位"]).group("分组字段", "聚合表达式")

/**【用法示例】**/
// 计算10分钟窗口内数据
def dataset = dataset.tumbleWindow("time", "10", "MINUTES").group("window_start,window_end","sum(price) as total")
// 计算10分钟窗口内数据，并设置偏移量
def dataset = dataset.tumbleWindow("time", "10", "MINUTES", "5", "MINUTES").group("window_start,window_end","sum(price) as total")
```

#### 2.4.5 滑动窗口算子

**hopWindow()**

滑动窗口算子，滑动窗口分配器将元素分配到固定长度的窗口中，与滚动窗口类似，另有滑动参数控制滑动窗口开始的频率。窗口有可能重叠。
用法如下：

```groovy
/**
 * 【语法说明】
 * 时间字段：列名，表示该列数据映射到滑动窗口。
 * 滑动时间：数字，指定连续滑动窗口之间的间隔时间。
 * 滑动时间单位：支持SECONDS，MINUTES，HOURS，DAYS
 * 窗口大小：数字，指定滑动窗口的窗口大小。
 * 窗口时间单位：支持SECONDS，MINUTES，HOURS，DAYS
 * 偏移量大小：可选参数，用于指定窗口开始移动的offset，也就是指定产生窗口的时间点。
 * 偏移量时间单位：可选参数，用于指定窗口偏移量时间单位。
 * 返回值：返回聚合操作之后的新数据集
 * 注：输入数据集要求为流表，窗口函数应该与聚合函数一起使用
 * 分组字段：分组字段只能为window_start和window_end
 */
def 数据集 = 数据集.hopWindow("时间字段", "滑动时间", "滑动时间单位", "窗口大小", "窗口时间单位" [,"偏移量大小","偏移量时间单位"]).group("分组字段", "聚合表达式")

/**【用法示例】**/
// 每5分钟开始计算10分钟内窗口内数据
def dataset = dataset.hopWindow("time", "5", "MINUTES", "10", "MINUTES").group("window_start,window_end","sum(price) as total")
// 每5分钟开始计算10分钟内窗口内数据，并设置偏移量
def dataset = dataset.hopWindow("time", "5", "MINUTES", "10", "MINUTES", "5", "MINUTES").group("window_start,window_end","sum(price) as total")
```

#### 2.4.6 累积窗口算子

**cumulateWindow()**

累积窗口算子用于将元素分配给随时间逐步扩展的窗口。每个新窗口在前一个窗口的基础上增加一个步长，直到达到预设的最大窗口大小。初始窗口从固定开始时间起始。

用法如下：

```groovy
/**
 * 【语法说明】
 * 时间字段：列名，表示该列数据映射到累积窗口。
 * 步长时间：数字，指定连续累积窗口结束时间之间增加的窗口大小的时间间隔。
 * 步长时间单位：支持SECONDS，MINUTES，HOURS，DAYS
 * 窗口大小：数字，指定累积窗口的窗口大小。大小必须是步长的整数倍。
 * 窗口时间单位：支持SECONDS，MINUTES，HOURS，DAYS
 * 偏移量大小：可选参数，用于指定窗口开始移动的offset，也就是指定产生窗口的时间点。
 * 偏移量时间单位：可选参数，用于指定窗口偏移量时间单位。
 * 返回值：返回聚合操作之后的新数据集
 * 注：输入数据集要求为流表，窗口函数应该与聚合函数一起使用
 * 分组字段：分组字段只能为window_start和window_end
 */
def 数据集 = 数据集.cumulateWindow("时间字段", "步长时间", "步长时间单位", "窗口大小", "窗口时间单位" [,"偏移量大小","偏移量时间单位"]).group("分组字段", "聚合表达式")

/**【用法示例】**/
// 每2分钟计算窗口内数据，10分钟输出一次10分钟窗口的总数据
def dataset = dataset.cumulateWindow("time", "2", "MINUTES", "10", "MINUTES").group("window_start,window_end","sum(price) as total")
// 每2分钟计算窗口内数据，10分钟输出一次10分钟窗口的总数据，并设置偏移量
def dataset = dataset.cumulateWindow("time", "2", "MINUTES", "10", "MINUTES", "2", "MINUTES").group("window_start,window_end","sum(price) as total")
```

### 2.5 控制类语法场景

#### 2.5.1 if/else条件控制

```groovy
/**
 * 【语法说明】
 * 判断条件，遵循groovy表达式范式
 */
if(判断条件1){
  // 条件为真时执行的代码块
}else if(判断条件2){
  // 第二个条件为真时执行的代码块
}else{
  // 所有条件都不满足时执行的代码块
}

/**【用法示例1】**/
//定义FMDB数据源
def fmdbDs = fmdb("fmdb-massdata")
//areaCode表示地区编码，通过任务提交接口的RunReq对象中的params参数注入，params是一个map结构，可以设置key=areaCode,value=320100
if (320100 == areaCode) {
	//模型1
	def df = from(fmdbDs, "user_nj")
			.where("update_time >= '2024-04-17' and areaCode = '320100'")
			.to(fmdbDs,'user_nj_new')
} else {
	//模型2
	def df = from(fmdbDs, 'user')
			.where("update_time >= '2024-04-17'")
			.to(fmdbDs,'user_js_new')
}
```

#### 2.5.2 for循环控制

```groovy
/**
 * 【语法说明】
 * 判断条件，遵循groovy表达式范式
 */
for(初始化; 判断条件; 更新){
  // 执行逻辑
}
/**【用法示例1】**/
// 定义FMDB数据源信息
def fmdb_local = fmdb("fmdb-massdata")
// 节点名称：淘沙自动化_国际通话表FMDB
def node_24_noalias = from(fmdb_local, "PHY_ADM_VMODEL_RES_BZDH_GJTH")
def node_24 = node_24_noalias.alias("node_24")
// 节点名称：统计
def node_25 = node_24.group("node_24.USER_CALL","AVG(node_24.SC) as F604")
        .where("F604 >10 and node_24.USER_CALL !=''")
        .select("node_24.USER_CALL as F603,F604")
        .sort("F603 desc,F604 desc")
        .nodeId("25")
//循环处理结果集node_25
for (int i = 0; i < node_25.data.rowSize(); i++) {
    DataFrame.Row row = node_25.data.getRow(i)
    def userCall = row.getValue("f603")
    def node_00 = node_24.where("node_24.USER_CALL = '" +userCall+"'")
    node_00.to(fmdb_local, "massdata.PHY_ADM_VMODEL_7lokptB_1121_"+i).nodeId("n"+i)
}
// 节点名称：统计-1
node_25.to(fmdb_local, "massdata.PHY_ADM_VMODEL_7lokptB_1121").nodeId("26")

/**【用法示例2】**/
//需要下发的地市编码
def areaCodes = ["320100","320200","320300","320400"]
//省厅数据源
def STFmdbDs = fmdb("/datasource/fmdbDruid.properties").areaCode("320000")
//批量给各个地市下发计算逻辑
for(String areaCode : areaCodes){
  //设置数据源所属地市，漂移计算判断依据
  def fmdbDs = fmdb("/datasource/fmdbDruid.properties").areaCode(areaCode)
  //计算逻辑，并将结果归集到省厅来
  def personDf = from(fmdbDs,"t_person")
				.where("id_card = '320323198809110214'")
				.select("name, id_card, age, "+areaCode+ " as areaCode")
				.to(STFmdbDs, "t_person_all")
}
/**【用法示例3】**/
//需要计算的表名t_qq、t_wx、t_bianfu
def tables = ["t_qq","t_wx","t_bianfu"]
//省厅数据源
def STFmdbDs = fmdb("/datasource/fmdbDruid.properties")
//批量给各个地市下发计算逻辑
for(String table : tables){
  //设置数据源所属地市，漂移计算判断依据
  def fmdbDs = fmdb("/datasource/fmdbDruid.properties")
  //计算逻辑，并将结果归集到省厅来
  def personDf = from(fmdbDs,table)
				.where("id_card = '320323198809110214'")
				.select("name, id_card, age, "+table+ " as table_name")
				.to(STFmdbDs, "t_person_all")
}
```

#### 2.5.3 信号量语法

信号量语法表示，在收到某个信号时，才会执行后续计算。目前信号量策略包括周期性调度信号、任务驱动信号、增量信号，用法如下：

##### 2.5.3.1 周期性信号

按照指定cron表达式设置，周期性发送信号执行后续计算

```groovy
/**
 * 【语法说明】
 * cron表达式：周期性调度表达式
 * 返回值：为自定义数据集, 后续对数据集的引用, 可用数据集变量代替 
 */
//场景1：信号量在某个节点后
def 数据集2 = 数据集.periodReactor(cron表达式)
//场景2：信号量在任务最前端；如果此数据集不被画布中任何数据集引用，则为全局周期性调度，即整个画布按此周期调度
def 数据集 = periodReactor(cron表达式)

/**【用法示例】**/
// 场景1
def tf = tornadoF("default", "210-tre")
def df1 = from(tf, "odbc.nb_mass_resource_http").nodeId("1")
def df2 = df1.where("APP_TYPE='500000042' ").select("ID, UPLOAD_AREA_CODE, APP_TYPE").nodeId("2")
def df3 = df2.to(tf, "massdata.test_magictable_20250318001", "ID string, UPLOAD_AREA_CODE string, APP_TYPE string").nodeId("3")
// 实时任务接周期性信号
def df4 = df3.periodReactor("0 0/3 * * * ?").nodeId("4")
// 每3分钟从magictable取一次数据
def fmdb_tre = fmdb("210-tre")
def df5 = df4.select("ID, UPLOAD_AREA_CODE, APP_TYPE").nodeId("5")
df5.to(fmdb_tre, "massdata.test_20250318001").nodeId("6")

// 场景2
def fmdb_local = fmdb("210-tre")
def fmdb_drift = fmdb("fmdb20D").areaCode("440800")
// 周期性信号，每3分钟执行一次漂移任务
def df = periodReactor("0 0/3 * * * ?").nodeId("0")
// 漂移任务
def df1 = from(fmdb_drift, "tre.stg_per_base").depend(df).alias("a").nodeId("1")
def df2 = df1.select("a.id_no, a.name, a.age, a.mobile").nodeId("2")
def df3 = from(fmdb_local, "tre.stg_mobile_base").alias("b").nodeId("3")
def df4 = df2.leftJoin(df3, "a.id_no = b.id_no ").select("a.id_no, a.name, a.age, b.mobile").nodeId("4")
df4.to(fmdb_local, "massdata.test_20250318xxx1").nodeId("3")

// 场景3  全局周期性调度
// 周期性信号，每3分钟执行一次任务  该df不会被任何算子引用或依赖
def df = periodReactor("0 0/3 * * * ?").nodeId("0")
def fmdb_local = fmdb("210-tre")
def df2 = from(fmdb_local, "tre.stg_person_base").alias("a").nodeId("3")
def df3 = from(fmdb_local, "tre.stg_mobile_base").alias("b").nodeId("3")
def df4 = df2.leftJoin(df3, "a.id_no = b.id_no ").select("a.id_no, a.name, a.age, b.mobile").nodeId("4")
df4.to(fmdb_local, "massdata.test_20250318xxx1").nodeId("3")
```

##### 2.5.3.2 任务驱动信号

当依赖的前置任务成功完成百分比满足条件立即发送信号执行后续计算，或者有一个前置任务成功完成后开始计时，延迟一定时间后发送信号执行后续计算

```groovy
/**
 * 【语法说明】
 * 模型ID集合：数组类型,依赖的前置任务的模型ID列表
 * 成功百分比：必选,1-100,整型值,默认100,依赖的前置任务成功百分比,任务成功比率达到这个百分比，立刻执行后置任务
 * 延迟时间：必选,>=0,整型值,单位:秒,默认0，当有一个前置任务成功完成后开始计时，达到延迟时间后，立刻执行后置任务
 * 返回值：为自定义数据集, 后续对数据集的引用, 可用数据集变量代替
 */
// 前置任务成功完成百分比达到设定值执行后置任务，或者有一个前置任务成功完成后开始计时，达到延迟时间后执行后置任务
def 数据集 = taskReactor(["模型ID1", "模型ID2"...],"成功完成百分比","延迟时间")

/**【用法示例】**/
def fmdb_local = fmdb("210-tre")

// 任务驱动信号，60%任务成功完成后立即执行后置任务，或者有一个任务成功完成时延迟30s执行后置任务
def df = taskReactor(["168BEAB1C5A5609C3D5713BA3C40B8C7","6469EFA281729D42EC2A1C27A768F627","4E12524BE74FDAF6B5AE08FB13F96E45"], 60, 30).nodeId("0")
def df2 = from(fmdb_local, "tre.stg_mobile_base").nodeId("1")
def df3 = df2.select("id_no, name, age, mobile").nodeId("2")
df3.to(fmdb_local, "massdata.test_20250318xxx1").nodeId("3")
```

##### 2.5.3.3 增量信号

周期性执行查询SQL，结果集条数满足命中条数或条数不足但达到最大等待时间，均执行后续计算

```groovy
/**
 * 【语法说明】
 * 数据源：执行查询SQL的数据源名称，用变量引用表示
 * 查询SQL：查询结果集SQL，查询结果字段必须包含"增量字段名"
 * 增量字段名：查询结果集的字段，用于判断增量条件
 * 命中条数：查询结果集的增量条数
 * 最大等待时间：距离第一条增量数据到达的时间阈值，单位秒
 * 返回值：为自定义数据集, 后续对数据集的引用, 可用数据集变量代替 
 */
def 数据集 = periodReactor(cron表达式)
//场景1：信号量在某个节点后
def 数据集2 = 数据集.increment(数据源, 查询SQL, 增量字段名, 命中条数, 最大等待时间)

/**【用法示例】**/
def periodDf = periodReactor("0 0/1 0-23 * * ? *")
def fmdbDs = fmdb()
def df = periodDf.increment(fmdbDs,
                " select id, id_no, name, age,capture_time from public.stg_per_base2 where age > 60 "
                , "capture_time"
                , 100
                , 300)
def df1 = from(fmdbDs,"${df.VAR_TEMP_TABLE}").depend(df).alias("ds1")
def df2 = df1.to(fmdbDs,"public.stg_per_base22")
```

### 2.6 自定义算子语法场景

#### 2.6.1 IP碰撞算子

针对两个数据集进行IP碰撞和过滤。

**ts_ipJoin()**

针对两个数据集的行内容进行IP碰撞和过滤，用法如下：

```groovy
/**
 * 【语法说明】
 * 数据集：用数据集变量引用表示
 * 关联条件：指定两个数据集进行关联的条件，关联条件写法遵循sql规范，关联条件也可以嵌套函数
 * 过滤条件：遵循sql的条件语法规范
 * 过滤字段：遵循sql的select语法规范，多个字段用逗号隔开的一个参数传递
 * 返回值：为自定义数据集名称，后续对数据集的引用，可用数据集变量代替 
 */
def 数据集 = ts_ipJoin(数据集A,数据集B,"关联条件","过滤条件","过滤字段")

/**【用法示例】**/
// 对两个数据集进行IP碰撞和过滤
def datasetA = from(fmdbDs, "ip_info1")
def datasetB = from(fmdbDs, "ip_info2")
def datasetRes = ts_ipJoin(datasetA,datasetB,"ip_info1.ip = ip_info2.ip",
        "ip_info2.time between '2024-12-20' and '2024-12-22'",
        "ip_info1.ip,ip_info2.time,...")
```

### 2.7 存储过程算子语法场景

**callProcedure()**

存储过程调用算子，用法如下：

```groovy
/**
 * 【语法说明】
 *  数据源：存储过程执行的数据源环境
 *  存储过程名称：指定调用哪个存储过程，支持[database_name.]procedure_name
 *  存储过程参数：支持0个或多个, 目前参数支持的类型是标识符，比如库名、表名之类的参数
 *  返回值：返回调用存储过程依赖的输入数据源
 */
 
def 数据源 = callProcedure(数据源, "存储过程名称" [,"参数1","参数2"...])

/**【用法示例】**/
// 调用存储过程
def procedureDs = callProcedure(fmdbDs, "dw_pro_standard", "STG_A_RY_CZRKXX_JBSJ_NEW") 
// 使用存储过程执行产生的结果表
def df1 = from(procedureDs, "STG_A_RY_CZRKXX_JBSJ_NEW")
```

### 2.8 内置变量语法场景

#### 2.8.1 系统变量

TSML中各类算子的返回的数据集对象为Dataframe对象，该对象对外暴露了一些可用的变量。

**VAR_TEMP_TABLE**

```groovy
/**
 * 【语法说明】
 *  临时表名，每个数据集对象都有一个全局唯一的临时表名，当数据集需要入库时，
 *  在不指定入库表名的时候，可以使用该临时表名，使用临时表名的数据在任务执行完成后会删除临时表。
 *  返回值：全局唯一的临时表名
 */
def tableName = 数据集.VAR_TEMP_TABLE

/**【用法示例】**/
// 定义fmdb数据源
def fmdbDs = fmdb()
// 定义数据集
def a = from(fmdbDs, "PHY_ADM_VMODEL_RES").alias("a").nodeId("1")
// 淘沙界面提取算子
def df1 = a.select("id","name", "lower(xuhao) as xuhao_lower").where("xuhao_lower != '123'").nodeId("3")
// 自定义SQL算子，需要对前置数据集结果进行自定义sql处理，此时不需要写to算子入库df1
// 可以借助df1数据集的临时表名来写查询SQL
def df2 = query(fmdbDs,"select * from ${df1.VAR_TEMP_TABLE}").depend(df1)
def df3 = df2.to(fmdbDs,"PHY_ADM_VMODEL_RES_0226")
```

#### 2.8.2 动态变量声明语法

通过variable命令，声明动态变量定义，在需要引用变量的地方，用${动态变量}形式进行占位引用，tre会在执行时对其进行实际值替换。动态变量声明时，需要传入变量生成器名称，以及生成器所需要的参数。变量生成器一般会要求传入动态变量的变量名，该变量名由用户指定，但是要求同一个tsml内要唯一，不能在多个动态变量声明中用相同变量名。变量可以在多处引用，并且在同一次执行中，不同位置引用的相同变量值相同。

```groovy
/**
 * 【语法说明】
 *  定义一个动态变量
 *  varGeneratorName：内置的变量生成器名称
 *  varParams：生成变量需要的参数及动态变量名,key:value结构map参数,可选
 *  返回值：包含动态变量名引用的变量对象
 */
def vars = variable(varGeneratorName[,varParams])

/**【用法示例】**/
// 声明变量
def partitionVars = variable("BdosPartitionIncrementVar",[ tableName:"fmdbmeta.adm_beh_place_hotel", partitionName:"p3", stepLength:10, partitionBeginVar:"domainP3Begin", partitionEndVar:"domainP3End"])

// 使用变量
def fmdb = fmdb("fmdb20D")
from(fmdb,"fmdbmeta.adm_beh_place_hotel")
    .where("p1='final' and p2='update' and p3>${partitionVars.domainP3Begin} and p3<=${partitionVars.domainP3End}")
    .to(fmdb,"increment_test_result")
```

**1.当前时间变量**

```groovy
/**
 * 当前时间变量 CurrentTimeVar,用task启动时间作为当前时间,支持格式化,
 * 默认不给timeFormat日期格式化pattern时,输出绝对秒,给timeFormat日期格式时,按格式输出。
 * 一个tsml可以按需定义多个当前时间变量,确保变量名不同即可。
 * 变量生成器:CurrentTimeVar
 * 生成器参数:
 *     timeVarName:当前时间变量的名称
 *     timeFormat:日期格式,可选,不填输出绝对秒, 例如:  yyyy-MM-dd HH:mm:ss
 */

/**【用法示例】**/
def timeVar = variable("CurrentTimeVar",[timeVarName:"today",timeFormat:"yyyy-MM-dd"])
def fmdb = fmdb("fmdb20D")
from(fmdb,"fmdbmeta.adm_beh_place_hotel")
    .where("p4 = '${timeVar.today}'")
    .to(fmdb,"increment_test_result")

// BDOS 中日期变量对应关系
// ${SEC} -> variable("CurrentTimeVar",[timeVarName:"SEC"])
// ${DATE8} -> variable("CurrentTimeVar",[timeVarName:"DATE8",timeFormat:"yyyyMMdd"])
// ${DATE10} -> variable("CurrentTimeVar",[timeVarName:"DATE10",timeFormat:"yyyyMMddHH"])
// ${DATE14} -> variable("CurrentTimeVar",[timeVarName:"DATE14",timeFormat:"yyyyMMddHHMMss"])
// ${DATE_8} -> variable("CurrentTimeVar",[timeVarName:"DATE_8",timeFormat:"yyyy-MM-dd"])
// ${DATE_14} -> variable("CurrentTimeVar",[timeVarName:"DATE_14",timeFormat:"yyyy-MM-dd HH:MM:ss"])

```

**2.增量抽取变量**

```groovy
/**
 * 通用增量字段抽取变量,CommonIncrementVar
 * 增量抽取变量会生成一个条件SQL片段, 
 *    首次抽取: field<=currentMaxValue; 
 *    增量抽取: lastMaxValue<field and field<=currentMaxValue; 
 *    表中无新数据时: LENGTH('tre') = 4; 
 *    表中无数据时: 1=1
 *
 * 变量生成器:CommonIncrementVar
 * 生成器参数:
 *     tableName:表名,需要带schema
 *     queryFieldName:字段名
 *     fieldAliasName:字段别名,可选,当字段被select as后,需要指定别名
 *	   execMinPoint:抽取指定范围的开始值,可选,支持 "select xx" 查询SQL
 *	   execMaxPoint:抽取指定范围的结束值,可选,支持 "select xx" 查询SQL
 */

/**【用法示例】**/
def captureTimeVar = variable("CommonIncrementVar", [tableName:"fmdbmeta.adm_beh_place_hotel", queryFieldName:"create_time", fieldAliasName:"captureTime"])
def fmdb = fmdb("fmdb20D")
from(fmdb,"fmdbmeta.adm_beh_place_hotel")
	.select("area_code as areaCode, create_time as captureTime, id, account")
    .where("areaCode='320100' and ${captureTimeVar}")
    .to(fmdb,"increment_test_result")

```

#### 2.8.3 BDOS专用分区抽取变量

**1.分区增量读取变量生成器 BdosPartitionIncrementVar**

```groovy
/**
 * 针对fmdb分区表,根据分区值进行分区增量抽取,产生分区开始和分区结束两个变量值
 * 变量生成器:BdosPartitionIncrementVar
 * 生成器参数:
 *		tableName:表名,需要带schema
 *		partitionName:分区名称
 *		stepLength:抽取步长,一次执行抽取多个分区
 *		execMinPoint:抽取指定范围分区的开始值,可选,支持 "select xx" 查询SQL
 *		execMaxPoint:抽取指定范围分区的结束值,可选,支持 "select xx" 查询SQL
 *		partitionBeginVar:分区开始变量名称
 *		partitionEndVar:分区结束变量名称
 */

def partitionVars = variable("BdosPartitionIncrementVar",[ tableName:"massdata.my_partition_table", partitionName:"p3", stepLength:10, execMinPoint:"1747418434", execMaxPoint:"1748368856", partitionBeginVar:"p3Begin", partitionEndVar:"p3End"])

def fmdb = fmdb("fmdb20D")

from(fmdb,"massdata.my_partition_table")
    .where("p1='final' and p2='update' and p3>${partitionVars.p3Begin} and p3<=${partitionVars.p3End}")
    .to(fmdb,"increment_test_result")
```

**2.分区修改时间增量读取变量生成器 BdosPartitionModifyIncrementVar**

```groovy
/**
 * 针对fmdb分区表,根据分区修改时间进行分区增量抽取,产生分区列表变量值
 * 变量生成器:BdosPartitionModifyIncrementVar
 * 生成器参数:
 *		tableName:表名,需要带schema
 *		partitionName:分区名称
 *		stepLength:抽取步长,一次执行抽取多个分区
 *		partitionListVar:分区列表变量名称
 */

def partListVars = variable("BdosPartitionModifyIncrementVar",[ tableName:"massdata.my_partition_table", partitionName:"p3", stepLength:10, partitionListVar:"partList"])

def fmdb = fmdb("fmdb20D")

from(fmdb,"massdata.my_partition_table")
    .where("p1='final' and p2='update' and p3 in (${partListVars.partList})")
    .to(fmdb,"increment_test_result")
```

**3.读取到最新N个分区变量生成器 BdosReadLastNPartitionVar**

```groovy
/**
 * 针对fmdb分区表,根据分区值排序,对最大N个分区进行增量抽取,产生分区开始和分区结束两个变量值
 * 变量生成器:BdosReadLastNPartitionVar
 * 生成器参数:
 *		tableName:表名,需要带schema
 *		partitionName:分区名称
 *		stepLength:抽取步长,N值来源,抽取最新多少个分区
 *		repeat:是否重复抽取,true:对已抽取的分区重复抽取,false:过滤已抽取的分区;可选,默认false
 *		partitionBeginVar:分区开始变量名称
 *		partitionEndVar:分区结束变量名称
 */

def partitionVars = variable("BdosReadLastNPartitionVar",[ tableName:"massdata.my_partition_table", partitionName:"p3", stepLength:10, repeat:true, partitionBeginVar:"p3Begin", partitionEndVar:"p3End"])

def fmdb = fmdb("fmdb20D")

from(fmdb,"massdata.my_partition_table")
    .where("p1='final' and p2='update' and p3>${partitionVars.p3Begin} and p3<=${partitionVars.p3End}")
    .to(fmdb,"increment_test_result")
```

**4.读取到最新分区变量生成器 BdosReadLastPartitionVar**

```groovy
/**
 * 针对fmdb分区表,读取到最后一个分区,产生分区开始和分区结束两个变量值
 * 变量生成器:BdosReadLastPartitionVar
 * 生成器参数:
 *		tableName:表名,需要带schema
 *		partitionName:分区名称
 *		execMinPoint:抽取指定范围分区的开始值,可选,支持 "select xx" 查询SQL
 *		execMaxPoint:抽取指定范围分区的结束值,可选,支持 "select xx" 查询SQL
 *		partitionBeginVar:分区开始变量名称
 *		partitionEndVar:分区结束变量名称
 */
// 示例一: 指定范围分区增量
def partitionVars = variable("BdosReadLastPartitionVar",[ tableName:"massdata.my_partition_table", partitionName:"p3", execMinPoint:"1747418434", execMaxPoint:"1748368856", partitionBeginVar:"p3Begin", partitionEndVar:"p3End"])

def fmdb = fmdb("fmdb20D")

from(fmdb,"massdata.my_partition_table")
    .where("p1='final' and p2='update' and p3>${partitionVars.p3Begin} and p3<=${partitionVars.p3End}")
    .to(fmdb,"increment_test_result")

// 示例二: 表全部分区增量
def partitionVars = variable("BdosReadLastPartitionVar",[ tableName:"massdata.my_partition_table", partitionName:"p3", partitionBeginVar:"p3Begin", partitionEndVar:"p3End"])

def fmdb = fmdb("fmdb20D")

from(fmdb,"massdata.my_partition_table")
    .where("p1='final' and p2='update' and p3>${partitionVars.p3Begin} and p3<=${partitionVars.p3End}")
    .to(fmdb,"increment_test_result")
```

**5.读取最新一个分区变量生成器 BdosReadLastOnePartitionVar**

```groovy
/**
 * 针对fmdb分区表,读取最新一个分区
 * 变量生成器:BdosReadLastOnePartitionVar
 * 生成器参数:
 *		tableName:表名,需要带schema
 *		partitionName:分区字段名称
 *		partitionVar:最新分区变量名称
 */
def lastPartitionVar = variable("BdosReadLastOnePartitionVar",[ tableName:"massdata.my_partition_table", partitionName:"p3", partitionVar:"lastP3"])

def fmdb = fmdb("fmdb20D")

from(fmdb,"massdata.my_partition_table")
    .where("p1='final' and p2='update' and p3=${lastPartitionVar.lastP3}")
    .to(fmdb,"increment_test_result")
```



### 2.9 大模型算子语法场景

tre大模型算子提供了直接对接大模型服务调用能力，通过大模型数据源定义、大模型服务调用和结果落库三个步骤完成大模型对数据的处理。

```groovy
/**
 *【大模型数据源语法说明】
 * url：模型api地址
 * concurrent：模型并发访问数量
 * areaCode：表示数据源的归属地，漂移计算会用到，是个可选属性，不设置默认为本地数据源
 * 返回值：大模型数据源变量
 */
def llmDs = llm(url,concurrent)[.areaCode("地市编码")]

/**【用法示例】**/
def llmDs = llm('http://172.17.62.1:28000/v1/chat/completions',10)
// tre支持默认大模型数据源配置，在tre.properties配置文件中, 如果想使用tre配置文件中的默认大模型数据源，通过如下写法实现
def defaultLlmDs = llm()
// 漂移场景下如果不知道对方tre对接的大模型配置，可以通过下面语法来定义大模型数据源，使用异地tre默认大模型数据源
def remoteLlmDs = llm().areaCode("异地xxx")


/**
 *【大模型调用语法说明】
 * llmDs：大模型数据源
 * modelName：模型名称
 * role：给大模型设定的角色,当不想设置角色时,请用空串代替
 * target：要求大模型完成的任务目标
 * resultColumn：存放大模型响应内容的字段名称
 * modelParams：传给大模型的其他参数，map<String,Object>类型，可选
 * 返回值：大模型结果数据集，数据集结构是df表全部字段加上resultColumn字段
 * 限制：调用大模型后的数据集必须立即to到当地的库中, 然后才可以进行其他处理
 */
def llmDf = df.llmCall(llmDs,modelName,role,target,resultColumn[,modelParams]).to(dataBaseDs,result_table)

/**【用法示例】**/
def fmdbDs = fmdb()
// 定义大模型数据源
def llmDs = llm('http://172.17.62.1:28000/v1/chat/completions',10)
// 查询出要处理的数据
def df = from(fmdbDs,"llm_model")
// 调用大模型服务,跟to算子将结果入库
def llmDf = df.llmCall(llmDs,'fiberhome-chat',"警察","判断用户输入描述中是否有提及危险物品，危险物品主要包括刀具、毒药、易燃易爆物三大类。注意为危险物品必须从原文提取且符合以上三类的定义。如果有提到危险物品，输出物品名称，否则输出“无”。隐藏分析的中间过程，直接给出危险物品的具体名称。","ai_response",["temperature":0.5,"top_p":1.0]).to(fmdbDs,"llm_result_20250305")

/**
 *【大模型接口认证用法示例】
 * 在modelParams里增加"apiKey":"xxxxx"参数,
 * apiKey的内容里填从平台申请的key,不用添加"Bearer "开头,tre后台会自动加
 */
def fmdbDs = fmdb()
// 定义大模型数据源
def llmDs = llm('http://172.17.62.1:28000/v1/chat/completions',10)
// 查询出要处理的数据
def df = from(fmdbDs,"llm_model")
// 调用大模型服务,跟to算子将结果入库
def llmDf = df.llmCall(llmDs,'fiberhome-chat',"警察","判断用户输入描述中是否有提及危险物品，危险物品主要包括刀具、毒药、易燃易爆物三大类。注意为危险物品必须从原文提取且符合以上三类的定义。如果有提到危险物品，输出物品名称，否则输出“无”。隐藏分析的中间过程，直接给出危险物品的具体名称。","ai_response",["temperature":0.5,"top_p":1.0,"apiKey":"my_api_key_xxxx"]).to(fmdbDs,"llm_result_20250305")

/**
 *【响应格式参数用法示例】
 * 在modelParams里增加"response_format":"xxxxx"参数,
 * response_format的内容是json字符串，包含type属性，例如"{\"type\":\"text\"}"。type常规可填"text"和"json_object"，默认是text。如果选择json_object，提示词中要指示大模型以json格式返回，否则可能无法正常工作。type填其他值的，如"json_schema"，请确认所连接的大模型支持改格式，并参考该大模型的接口文档，组织json_schema的结构描述和提示词注意事项。
 */
def fmdbDs = fmdb()
// 定义大模型数据源
def llmDs = llm('http://172.17.62.1:28000/v1/chat/completions',10)
// 查询出要处理的数据
def df = from(fmdbDs,"llm_model")
// 调用大模型服务,跟to算子将结果入库
def llmDf = df.llmCall(llmDs,'fiberhome-chat',"警察","判断用户输入描述中是否有提及危险物品，危险物品主要包括刀具、毒药、易燃易爆物三大类。注意为危险物品必须从原文提取且符合以上三类的定义。如果有提到危险物品，输出物品名称，否则输出“无”。隐藏分析的中间过程，直接给出危险物品的具体名称。","ai_response",["temperature":0.5,"top_p":1.0,"response_fromat":"{\"type\":\"text\"}"]).to(fmdbDs,"llm_result_20250305")
```



**tre默认大模型数据源配置**

tre支持默认大模型数据源配置，在tre.properties中配置，配置项片段如下。另外该配置支持treClient.updateTreConfig接口动态修改

```properties
## tre默认大模型数据源 ##
# 大模型api地址
tre.llm.url=http://172.17.62.1:28000/v1/chat/completions
# 大模型并发访问数量
tre.llm.concurrent=10
# 大模型响应超时时间,单位秒
tre.llm.response.timeout=1800
```



## 3 BNF范式

```bnf
<TSML> ::= { <Statement> ';'? }

<Statement> ::= 
    <VariableDeclaration> 
    | <DataSourceDeclaration> 
    | <DataInitialization> 
    | <OperatorChain> 
    | <ControlStructure> 
    | <ReturnOperation> 
    | <Comment>

<VariableDeclaration> ::= 
    "def" <VarName> '=' <VariableExpression>

<VariableExpression> ::= 
    "variable" '(' <VarGenerator> ',' <Parameters> ')' | <VarName> '.' "VAR_TEMP_TABLE"

<VarGenerator> ::= "CurrentTimeVar" 
				| "BdosPartitionIncrementVar" 
				| "CommonIncrementVar" 
				| "BdosPartitionModifyIncrementVar" 
				| "BdosReadLastNPartitionVar" 
				| "BdosReadLastPartitionVar"

<Parameters> ::= '[' <ParameterList> ']' 
<ParameterList> ::= <Parameter> ( ',' <Parameter> )*
<Parameter> ::= <KeyName> ':' <Value>

<KeyName> ::= <StringLiteral>
<Value> ::= <StringLiteral> | <DigitLiteral>

<DataSourceDeclaration> ::= 
    "def" <VarName> '=' <DataSourceInit> ( '.' <DSMethod> )* 

<DataSourceInit> ::= 
    <DataSourceType> '(' <InitArgs>? ')'
<DataSourceType> ::= 
    "fmdb" | "postgres" | "hivehw" | "tornadoF" | 
    "search" | "llm" | "ark" | "callProcedure"

<InitArgs>        ::= <FMDBArgs> 
                   | <PostgresArgs>
                   | <HivehwArgs>
                   | <TornadoFArgs>
                   | <SearchArgs>
                   | <LlmArgs>
                   | <ArkArgs>
                   | <CallProcedureArgs>

<FMDBArgs>      ::= [ <StringLiteral> ] 
<PostgresArgs>  ::= <StringLiteral> ',' <DigitLiteral> ',' 
                    <StringLiteral> ',' <StringLiteral> ',' <StringLiteral>
<HivehwArgs>      ::= [ <StringLiteral> ] 
<TornadoFArgs>      ::= [ <StringLiteral> ',' <StringLiteral> ] 
<SearchArgs>    ::= [ <StringLiteral> ',' <DigitLiteral> ]
<LlmArgs>    ::= [ <StringLiteral> ',' <DigitLiteral> ]
<ArkArgs>    ::= [ <StringLiteral> ',' ] <StringLiteral> ',' <VarName> ]
<CallProcedureArgs>    ::= <VarName> ',' <StringLiteral>  ( ',' <StringLiteral> )*

<DSMethod> ::= 
    "areaCode" '(' <StringLiteral> ')' 
    | ( [ "inputParams" '(' <ParameterList> ')' ]
    | [ "columnParams" '(' <ParameterList> ')' ]
    | [ "otherParams" '(' <ParameterList> ')' ]
    | "outputParams" '(' <ParameterList> ')' )

<DataInitialization> ::= 
    "def" <VarName> '=' <FromClause> ( '.' <DatasetInitMethod> )* 

<FromClause> ::= 
    ( "from" | "fromArk" ) '(' <VarName> ',' <StringLiteral> ')'
    | "fromSearch" '(' <VarName> ',' <StringLiteral> 
    				',' <StringLiteral> ',' <StringLiteral> 
    				',' ( <StringLiteral> | '[' <StringLiteral> ( ',' <StringLiteral> )* ']' ) 
    				[ ',' <StringLiteral> ] ')' 
    			'.' "to" '(' <VarName> ',' <StringLiteral> ')'
    | "query" '(' <VarName> ',' <StringLiteral> ')'
    | "insert" '(' <VarName> ',' <StringLiteral> ',' <StringLiteral> ')'
    | "periodReactor" '(' <StringLiteral> ')'
    | "taskReactor" '(' '[' <StringLiteral> ( ',' <StringLiteral> )* ']' 
    				[ ( ',' <DigitLiteral> | ',' <DigitLiteral>  ',' <DigitLiteral> ) ] ')'
    | "ts_ipJoin" '(' <VarName> ',' <VarName> 
    				',' <StringLiteral> ',' <StringLiteral> ',' <StringLiteral> ')'

<DatasetInitMethod> ::= 
    "alias" '(' <StringLiteral> ')'
    | "nodeId" '(' <StringLiteral> ')'
    | "depend" '(' <VarName> ( ',' <VarName> )* ')'

<OperatorChain> ::= [ "def" <VarName> '=' ]
    <VarName> '.' <Operator> ( '.' <Operator> )*

<Operator> ::= 
    <SimpleOp> | <ComplexOp>

<SimpleOp> ::=        
    "where" '(' <StringLiteral> ')'
  | "select" '(' <StringLiteral> ( ',' <StringLiteral> )* ')'
  | "nodeId" '(' <StringLiteral> ')'
  | "withColumn" '(' <StringLiteral>  ',' <StringLiteral> ')'
  | ( "group" | "groupSortFirst" ) '(' <StringLiteral> ',' <StringLiteral> ')'
  | "sort" '(' <StringLiteral> ',' <StringLiteral> ')' ['.' "index" '(' <StringLiteral> ')' ]
  | "distributeSort" '(' <StringLiteral> ',' <StringLiteral> ')'
  | "limit" '(' <DigitLiteral> [ ',' <DigitLiteral> ] ')'
  | "distinct" '(' [ <StringLiteral> ( ',' <StringLiteral> )* ] ')'
  | ( "union" | "unionAll" ) '(' <VarName> ')'
  | ( "subtract" | "subtractAll" | "intersect" | "intersectAll" ) '(' <VarName> 
  										[ ',' <StringLiteral> ] ')'
  | "join" '(' <VarName> ',' <StringLiteral> [ ',' <StringLiteral> ] ')'
  | ( "leftJoin" | "rightJoin" | "fullJoin" ) '(' <VarName> ',' <StringLiteral> ')'
  | （ "exists" | "notExists" ) '(' <VarName> ',' <StringLiteral> ')'
  | "to" '(' <VarName> ',' <StringLiteral> ')' [ '.' "fields" '(' <StringLiteral> ')' ]
					[ '.' "ttl" '(' <DigitLiteral> ')' ] 
					( [ '.' "overwrite" '(' ')' ]
					| [ '.' "partition" '(' <StringLiteral> ')' ]
					| [ '.' "upsert" '(' ')' ] ) 
  | "periodReactor" '(' <StringLiteral> ')'
 
<ComplexOp> ::= 
   "tumbleWindow" '(' <StringLiteral> ',' <StringLiteral> ',' <StringLiteral> 
   				[ ',' <StringLiteral> ',' <StringLiteral> ] ')' 
   			'.' "group" '(' <StringLiteral>  ',' <StringLiteral> ')'
  | ( "hopWindow" | "cumulateWindow" ) '(' <StringLiteral> ',' <StringLiteral> 
  				',' <StringLiteral> ',' <StringLiteral> ',' <StringLiteral> 
  				[ ',' <StringLiteral> ',' <StringLiteral> ] ')' 
  				'.' "group" '(' <StringLiteral>  ',' <StringLiteral> ')'
  |  "llmCall" '(' <VarName> ',' <StringLiteral> ',' <StringLiteral> ',' <StringLiteral> ',' <StringLiteral> 
   				[ ',' <StringLiteral> ',' <StringLiteral> ] ')' 
   			'.' "group" '(' <StringLiteral>  ',' <StringLiteral> ')'

<ControlStructure> ::= 
    <IfStmt> | <ForLoop>

<IfStmt> ::= 
    "if" '(' <Condition> ')' <Block> 
    { "else if" '(' <Condition> ')' <Block> } 
    [ "else" <Block> ]

<ForLoop> ::= 
    "for" '(' <Init> ';' <Condition> ';' <Update> ')' <Block>

<ReturnOperation> ::= "retrunDf" '(' <VarName> ')' 

<Comment> ::= "/*" .* "*/" | "//" .* | "comment" '(' <StringLiteral> ')'

<StringLiteral> ::= '"' .*? '"' | '"""' .*? '"""'
<VarName> ::= [a-zA-Z_][a-zA-Z0-9_]* 
<DigitLiteral> ::= [0-9]+
```

**说明：**

1. **变量定义** (`<VariableDeclaration>`):
   - 通过variable生成器如`CurrentTimeVar`声明动态变量，参数以键值对形式传递。
2. **数据源定义** (`<DataSourceDeclaration>`):
   - 包括fmdb、postgres等数据源，支持参数和可选的.areaCode修饰符。
   - 例如：`def ds = fmdb().areaCode("320100")`
3. **数据集初始化** (`<DataInitialization>`):
   - 使用`from()`从数据源获取数据集，附加alias和depend等方法。
   - 例子：`def df = from(ds, "table").alias("a").depend(otherDF)`
4. **算子链** (`<OperatorChain>`):
   - 支持各种操作符如select、where、group、join等，以链式调用。
   - 例子：`df.select("age").where("age > 10").sort("age desc")`
5. **控制结构** (`<ControlStructure>`):
   - if/else, for循环支持嵌套和复杂条件。
6. **返回算子** (`<ReturnOperation>`):
   - `returnDf(df)` 返回数据内容到前端调用方。
7. **注释** (`<Comment>`):
   - 支持注释算子`comment("""注释内容""")` 和注释符号两种。
8. **节点ID** (`<NodeId>`):
   - 可附加在任何算子链末尾，例如`.nodeId("NODE_001")`。

**注**：此BNF简化了某些复杂结构（如嵌套参数），但覆盖了文档中提到的主要语法元素。实际应用中需根据具体参数规则进一步细化。



