## 5 本体使用方式

### 5.1 SDK调用方式

#### 5.1.1 查询本体信息

TRE对外提供的SDK JAR包中增加了获取本体查询接口。

```java
/**
 * 查询本体信息
 * @param fullOntologyNames 指定本体完整名称,多个逗号分隔,不传查询全部本体
 * @return 本体信息列表
 */
public abstract List<OntoInfoRsp> getOntologies(String fullOntologyNames);
```

接口入参：

| 属性                | 类型     | 描述                              |
| ----------------- | ------ | ------------------------------- |
| fullOntologyNames | String | 指定本体完整名称,多个逗号分隔,不传(给null)查询全部本体 |

List<OntoInfoRsp>查询接口返回本体：

| 属性              | 类型               | 描述                                       |
| --------------- | ---------------- | ---------------------------------------- |
| oId             | String           | 本体ID                                     |
| oName           | String           | 本体名称                                     |
| oDesc           | String           | 本体描述                                     |
| oTable          | String           | 本体物理表名                                   |
| oAuthor         | String           | 本体作者                                     |
| version         | String           | 版本(对应package)                            |
| className       | String           | 本体类名称(不包含包名)                             |
| parentClass     | String           | 父类,完整路径,为null表示继承java.lang.Object或者集成tre的本体基类tre.Ontology,是递归停止条件 |
| fields          | List<OntoField>  | 本体属性                                     |
| -\|fieldName    | String           | 本体属性名称                                   |
| -\|fieldType    | String           | 属性类型,java数据类型                            |
| -\|columnName   | String           | 列名称                                      |
| -\|dataTypeName | String           | 数据类型名称，默认值为String                        |
| -\|remarks      | String           | 列说明                                      |
| methods         | List<OntoMethod> | 本体方法信息                                   |
| -\|methodName   | String           | 方法名称                                     |
| -\|methodDesc   | String           | 方法描述                                     |
| -\|returnType   | String           | 返回值类型                                    |
| -\|params       | String[]         | 方法参数列表, 包含参数类型和参数名                       |

List<OntoInfoRsp>样例：

```
[
    {
      "oId": "TS_00001_00001",
      "oName": "群聊本体",
      "oDesc": "群聊本体包含属性：群组ID（groupId）、微信ID（wxId）、聊天内容（content）",
      "oTable": "t_chat",
      "oAuthor": "X0001",
      "version": "v1",
      "className": "Chat",
      "parentClass": null,
      "fields": [
        {
          "fieldName": "groupId",
          "fieldType": "java.lang.String",
          "columnName": "group_id",
          "dataTypeName": "string",
          "remarks": "群标识"
        },
        {
          "fieldName": "wxId",
          "fieldType": "java.lang.String",
          "columnName": "wx_id",
          "dataTypeName": "string",
          "remarks": "微信标识"
        },
        {
          "fieldName": "content",
          "fieldType": "java.lang.String",
          "columnName": "content",
          "dataTypeName": "string",
          "remarks": "聊天内容"
        }
      ],
      "methods": [
        {
          "methodName": "loadData",
          "methodDesc": " 内置全部外部数据加载逻辑\n @return 本体数据集",
          "returnType": "CmdDataframe",
          "params": []
        },
        {
          "methodName": "loadData",
          "methodDesc": " 内置部分外部数据加载逻辑\n @return 本体数据集",
          "returnType": "CmdDataframe",
          "params": [
            "CmdDataframe outerData"
          ]
        },
        {
          "methodName": "filterKeywords",
          "methodDesc": " 查找包含指定关键词的群有哪些\n @param keywords 关键词\n @param tableName 结果输出的表名",
          "returnType": "java.lang.Object",
          "params": [
            "String keywords",
            "String tableName"
          ]
        }
      ]
    }
  ]
```

#### 5.1.2 提交任务

同sdk提交tsml脚本任务方式， 参考 tre集成指导手册V1.4 第4章节提交任务部分。

#### 5.1.3 获取本体查询结果

TRE对外提供的SDK JAR包中增加了获取本体查询结果接口。

```java
// 获取本体查询结果
TaskResult treClient.getTaskResult(String taskId)
```

接口入参：

| 属性     | 类型     | 描述       |
| ------ | ------ | -------- |
| taskId | String | 本体查询任务ID |

TaskResult查询接口返回本体：

| 属性                  | 类型                        | 描述             |
| ------------------- | ------------------------- | -------------- |
| status              | String                    | 查询任务状态         |
| result              | List                      | 查询结果           |
| -\|areaCode         | String                    | 本体查询的地市        |
| -\|code             | String                    | 本体查询响应编码       |
| -\|msg              | String                    | 本体查询响应信息       |
| -\| data            | List<Map<String, Object>> | 数据内容           |
| -\|  metadata       | List                      | 元数据            |
| -\| columnIndex     | int                       | 列下标            |
| -\| columnName      | String                    | 列名称            |
| -\| dataTypeName    | String                    | 字段数据库层面类型:类型名称 |
| -\| jdbcType        | int                       | 字段jdbc类型       |
| -\| columnLength    | int                       | 字段长度           |
| -\| decimalDigits   | int                       | 精度             |
| -\| remarks         | String                    | 注释             |
| -\| nullable        | boolean                   | 是否允许为空         |
| -\| defaultValue    | String                    | 默认值            |
| -\| partition       | boolean                   | 是否为分区字段        |
| -\| primaryKey      | boolean                   | 是否为主键          |
| -\| autoGenerated   | boolean                   | 是否自增长          |
| -\| primaryKeyIndex | int                       | 主键序号           |

status任务状态字典:

| 状态码              | 名称       |
| ---------------- | -------- |
| NEW              | 新建       |
| PENDING          | 等待中      |
| RUNNING          | 运行中      |
| FINISHED         | 结束（全部成功） |
| PARTIAL_SUCCESS  | 结束（部分成功） |
| EXCEPTION        | 结束（全部异常） |
| MANUAL_STOPPED   | 手动停止     |
| OVERTIME_STOPPED | 超时停止     |
| CANCELLED        | 取消执行     |

code响应编码字典：

| 状态码      | 名称      |
| -------- | ------- |
| TRE_2000 | 成功      |
| TRE_0101 | 本体未注册   |
| TRE_0102 | 数据库执行失败 |
| ...      | ...     |

TaskResult样例：

```
{
	"status": "PARTIAL_SUCCESS",
	"result": [{
		"areaCode": "320100",
		"code": "TRE_2000",
		"msg": "成功",
		"data": [{
			"name": "xiaoming"
		},
		{
			"name": "xiaohong"
		}],
		"metadata": [{
			"columnIndex": 1,
			"columnName": "name",
			"remarks": "姓名",
			"dataTypeName": "varchar",
			"jdbcType": 12,
			"columnLength": 255
		}]
	},
	{
		"areaCode": "320200",
		"code": "TRE_0100",
		"msg": "本体未注册",
		"data": [],
		"metadata": []
	}]
}
```

### 

