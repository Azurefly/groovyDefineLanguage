## 4 本体CURD语法定义

### 4.1 本体插入数据

**save()：** 用于插入数据到本体

```groovy
/**
 * 【语法说明】
 * 本体：用本体对象变量引用表示
 * 属性集合:本体属性和值的映射集合,如[name:"xiaoming",age:"12"]
 */
本体.save(属性集合)
/**【用法示例】**/
// save
v1.Chat chat = new v1.Chat(oDs:fmdb)
chat.save([id:"1",name:"xiaoming",age:"12"])
```

### 4.2 本体删除数据

**delete()：**用于删除本体的数据

```groovy
/**
 * 【语法说明】
 * 本体：用本体对象变量引用表示
 * 条件：遵循sql的条件语法规范
 */
本体.delete(属性集合)
/**【用法示例】**/
// delete
v1.Chat chat = new v1.Chat(oDs:fmdb)
chat.delete("id = 1")
```

### 4.3 本体更新数据

**update()：**用于更新本体的数据

```groovy
/**
 * 【语法说明】
 * 本体：用本体对象变量引用表示
 * 属性集合:本体属性和值的映射集合,如[name:"xiaoming",age:"12"]
 * 条件：遵循sql的条件语法规范
 */
本体.update(属性集合)
/**【用法示例】**/
// update
v1.Chat chat = new v1.Chat(oDs:fmdb)
chat.update("id = 1",[name:"xiaoming",age:"12"])
```

### 4.4 本体查询数据

#### 4.4.1 基础查询

##### 4.4.1.1 过滤算子

**where()**

针对本体的行内容进行过滤，用法如下：

```groovy
/**
 * 【语法说明】
 * 本体：用本体对象变量引用表示
 * 过滤条件：遵循sql的where条件语法规范
 * 返回值：为自定义数据集名称，后续对数据集的引用，可用数据集变量代替 
 */
def 数据集 = 本体.where("过滤条件")

/**【用法示例】**/
// 对本体进行过滤
v1.Chat chat = new v1.Chat(oDs:fmdb)
def df = chat.where("age >0 and age < 150")
```

**select()**

针对本体的列（字段）进行过滤，用法如下：

```groovy
/**
 * 【语法说明】
 * 本体：用本体对象变量引用表示
 * 过滤字段表达式：遵循sql的select语法规范，多个字段可用逗号隔开的一个参数传递，也可以多个参数传递
 * 返回值：为自定义数据集，后续对数据集的引用，可用数据集变量代替 
 */
def 数据集 = 本体.select("过滤字段表达式1"[,"过滤字段表达式2"])

/**【用法示例】**/
//对本体进行过滤
v1.Chat chat = new v1.Chat(oDs:fmdb)
def dataset = chat.select("age","name as person_name","replace(email,'@fh.com','@fiberhome.com') as email")
```

**mapping()**

针对本体的列（字段）进行过滤，类同select()，更直观的展示过滤字段和其别名，用法如下：

```groovy
/**
 * 【语法说明】
 * 本体：用本体对象变量引用表示
 * 过滤字段表达式：遵循sql的select语法规范，多个字段可用逗号隔开的一个参数传递
 * 返回值：为自定义数据集，后续对数据集的引用，可用数据集变量代替 
 */
def 数据集 = 本体.mapping(别名: "过滤字段表达式1"[,别名: "过滤字段表达式1"])

/**【用法示例】**/
//对数据集进行过滤
v1.Chat chat = new v1.Chat(oDs:fmdb)
def dataset = chat.mapping(
  age: "age",
  person_name: "name",
  email: "replace(email,'@fh.com','@fiberhome.com')"
)
```

##### 4.4.1.2 聚合算子

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
def 数据集 = 本体.group("分组字段", "聚合表达式")

/**【用法示例】**/
v1.Chat chat = new v1.Chat(oDs:fmdb)
// 数据集分组, 按照身份证号分组, 统计每个人的手机号数量
def datasetA = chat.group("sex", "count(sex) as count_sex")
//SQL语法中group后的having，等价于where，使用where代替
def datasetB = chat.group("sex", "count(sex) as count_sex").where("count_sex > 10")
def datasetC = chat.group("class,sex", "avg(age) as ave_age")
// 合并行操作
/**
 * concat_ws 函数说明
 * 样例：concat_ws('###',sort_array(collect_list(mobile))) 
 * ###：合并行的字段内容的分隔符,mobile：合并行的字段
 */
def datasetD = chat.group("idNo,name,age", "concat_ws('###',sort_array(collect_list(mobile))) as mobile")


```

##### 4.4.1.3 排序算子

**sort()**

对记录进行排序，用法如下：

```groovy
/**
 * 【语法说明】
 * 排序字段：字段名 asc|desc
 * index （可选）: 在淘沙场景下，排序后的数据集中，会增加一列序号字段。
 * 返回值：返回排序后的新数据集
 */
def 数据集 = 本体.sort("排序字段1"[, "排序字段2"])[.index("序号字段名")]

/**【用法示例】**/
v1.Chat chat = new v1.Chat(oDs:fmdb)
// 根据id_no降序和age升序,对记录排序
def dataset = chat.sort("idNo desc", "age asc")
//排序后增加一列排序字段，字段名为rowNum
def dataset = chat.sort("idNo desc", "age asc").index("rowNum")
```

##### 4.4.1.4 分页算子
**limit()**

限制查询结果条数，用法如下：

```groovy
/**
 * 【语法说明】
*  起始位置: 记录下标,第一条是0
 * 查询条数：要控制返回的记录条数
 * 返回值：新数据集
 */
def 数据集 = 本体.limit([起始位置,] 查询条数)

/**【用法示例】**/
v1.Chat chat = new v1.Chat(oDs:fmdb)
// top n
def datasetA = chat.limit(1000)
// paging
def datasetB = chat.limit(0,50)
```

##### 4.4.1.5 去重算子

**distinct()**

对数据去重，用法如下：

```groovy
/**
 * 【语法说明】
*  去重字段: 用于判断记录重复的字段,可选,不填则全部字段去重
 * 返回值：新数据集
 */
def 数据集 = 本体.distinct([去重字段1,去重字段2,...])

/**【用法示例】**/
v1.Chat chat = new v1.Chat(oDs:fmdb)
// 全量字段去重
def datasetA = chat.distinct()
// 指定字段去重
def datasetB = chat.distinct("idNo","name")
```

#### 4.4.2 漂移查询

本体基类提供基类方法设置漂移地市：areaCode()。

**areaCode()：**用于设置本体漂移的地市，该方法入参是字符串可变长参数

```groovy
/**
 * 【语法说明】
 * 本体：用本体对象变量引用表示
 * 地市编码：指定本体的漂移地市,字符串可变长参数
 */
本体.areaCode("地市编码A","地市编码B")
/**【用法示例】**/
v1.Chat chat = new v1.Chat(oDs:fmdb)
chat.areaCode("320100","320200")
chat.where("id = 1")
```

#### 4.4.3 返回查询结果

本体基类提供基类方法返回查询结果：returnDf()。

**returnDf()：**用于将本体的查询结果集返回给调用客户端，用法如下：

```groovy
/**
 * 【语法说明】
 * 数据集：需要返回的数据集，用数据集变量引用表示
 */
本体.returnDf()

/**【用法示例】**/
v1.Chat chat = new v1.Chat(oDs:fmdb)
chat.where("id = 1")
chat.returnDf()
```

### 4.3 本体查询结果分析

​	本体查询结果与基础算子结合进行分析使用。

​	需要使用本体基类提供的基类方法获取查询结果集：getQueryDataframe()

​	**getQueryDataframe():**  获取本体查询结果数据集CmdDataframe

```groovy
/**
 * 【语法说明】
 * 本体：用本体对象变量引用表示
 * 地市编码：指定本体的漂移地市，为空表示获取本体指定的所有漂移地市查询结果集
 */
def df = 本体.getQueryDataframe("地市编码A","地市编码B")
/**【用法示例】**/
v1.Chat chat = new v1.Chat()
chat.areaCode("320100","320200")
chat.where("id = 1")
def df = chat.getQueryDataframe()
def fmdb = fmdb().areaCode("320100")
df.where("name = 'xiao'").to(fmdb,"result_table")
```



