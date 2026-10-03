### 5.2 Http接口调用方式

#### 5.2.1 查询本体信息

**服务接口地址：**

http://[IP]:[PORT]/tre/api/getOntologies

**请求类型：**

GET

**请求头：**

需要传递token，与api请求相同，可联系tre获取

tre-token=xxxx

接口入参：

| 属性                | 类型     | 描述                       |
| ----------------- | ------ | ------------------------ |
| fullOntologyNames | String | 指定本体完整名称,多个逗号分隔,不传查询全部本体 |
| areaCode          | String | 地市编码，不传默认查询服务节点所在地市编码    |

Respone查询接口返回本体：

| 属性   | 类型                | 描述                                   |
| ---- | ----------------- | ------------------------------------ |
| code | int               | 结果码，0：请求成功，-1：请求失败                   |
| msg  | String            | 请求结果：操作成功/操作失败                       |
| data | List<OntoInfoRsp> | 返回的本体信息，同5.1.1章节下List<OntoInfoRsp>结构 |

Respone样例：

```
{
  "code": 0,
  "msg": "操作成功",
  "data": [
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
  ],
  "success": true
}
```



### 5.3 MCP调用方式

#### 5.3.1 MCP使用配置

**mcp服务地址：**

http://[IP]:[PORT]/tre/mcp/service

**MCP类型：**

可流式传输的HTTP(streamableHttp)

#### 5.3.2 MCP下发本体执行脚本工具

mcp工具方法一般为大模型调用，通过mcp工具获取

**工具方法名：**

start_task

**输入参数：**

> **实现说明（以代码为准）：** 当前 `StartTaskTool` 的 MCP schema 实际仅声明 `code`（TSML 脚本内容，必填）与 `params`（脚本参数，非必填）两个字段；历史文档中的 `bussinessId`（含拼写错误）等字段已从 schema 移除，下表为历史 TSML 协议参数说明，仅供参考。

| 参数名                     | 类型      | 说明                                       |
| ----------------------- | ------- | ---------------------------------------- |
| **traceId**             | string  | 链路标识,非必填                                 |
| **bussinessId**         | string  | 业务标识, BDP应用菜单编码,必填,默认AI-MCP              |
| **codeId**              | string  | TSML脚本标识,非必填                             |
| **code**                | string  | TSML脚本内容, 必填                             |
| **userLevel**           | string  | 任务优先级,值[1-9], 值越大优先级越高,TRE默认3,非必填        |
| **modelId**             | string  | 模型业务标识,必填，无标识则填入uuid                     |
| **maxRunTime**          | string  | 实时任务最大运行时间,单位:天,仅实时任务生效;不传时,TRE默认7天,非必填  |
| **resumeFromException** | boolean | 任务执行异常,再次提交或下次周期运行时,是否继续上次异常节点重新运行,非必填   |
| **params**              | string  | 脚本参数,非必填,多个参数分割,举例：xx1=xx;xx2=xxx        |
| **userId**              | string  | 用户标识,非必填                                 |
| **extractEngine**       | string  | 实时任务标识,tornadoF平台会把有相同标识的模型进行统一优化处理,非必填，多个标识分号分割，举例：type=FZ;xxx=xxx |
| **tableTtl**            | string  | 分区数据保留时间,单位:秒,仅实时任务生效;不传时,TRE默认86400秒即1天,非必填 |

**使用样例：**

通过大模型工具下发任务，如果需要返回数据，代码中必须要有returnDf()，以cherrystudio为例：

![mcp-下发本体tsml](assets\mcp-下发本体tsml.png)



**返回结果：**

"{\"taskId\":\"2365fb670aad4d748975c48d48d84ebf\"}"

#### 5.3.3 MCP获取本体查询结果

mcp工具方法一般为大模型调用，通过mcp工具获取

**工具方法名：**

get_task_result

**输入参数：**

| 参数名        | 类型     | 说明      |
| ---------- | ------ | ------- |
| **taskId** | string | 任务id,必填 |

**使用样例：**

通过大模型工具下发任务，以cherrystudio为例：

![mcp-查询本体结果](assets\mcp-查询本体结果.png)



**返回结果：**

同5.1.1 获取本体查询结果返回的结果

#### 5.3.4 通过TSML获取DAG图

mcp工具方法一般为大模型调用，通过mcp工具获取

**工具方法名：**

get_tsml_to_dag

**输入参数：**

| 参数名      | 类型     | 说明        |
| -------- | ------ | --------- |
| **code** | string | TSML脚本,必填 |

**使用样例：**

通过大模型工具下发任务，以cherrystudio为例：

![mcp-查询本体结果](assets\mcp-tsml转dag图.png)



**返回结果：**

返回结果为点线边信息，可以使用butterfly-dag插件进行图片展示，图结果如下：

![mcp-查询本体结果](assets\mcp-tsml转dag图结果.png)

返回数据结果如下：

```
{
    "canvas": {
        "edges": [
            {
                "arrow": true,
                "arrowPosition": 1,
                "hasRadius": true,
                "shapeType": "AdvancedBezier",
                "source": "bottom",
                "sourceNode": "operator-1",
                "target": "top",
                "targetNode": "operator-3",
                "type": "endpoint"
            },
            {
                "arrow": true,
                "arrowPosition": 1,
                "hasRadius": true,
                "shapeType": "AdvancedBezier",
                "source": "bottom",
                "sourceNode": "operator-3",
                "target": "top",
                "targetNode": "operator-4",
                "type": "endpoint"
            },
            {
                "arrow": true,
                "arrowPosition": 1,
                "hasRadius": true,
                "shapeType": "AdvancedBezier",
                "source": "bottom",
                "sourceNode": "operator-4",
                "target": "top",
                "targetNode": "operator-5",
                "type": "endpoint"
            },
            {
                "arrow": true,
                "arrowPosition": 1,
                "hasRadius": true,
                "shapeType": "AdvancedBezier",
                "source": "bottom",
                "sourceNode": "operator-5",
                "target": "top",
                "targetNode": "operator-6",
                "type": "endpoint"
            },
            {
                "arrow": true,
                "arrowPosition": 1,
                "hasRadius": true,
                "shapeType": "AdvancedBezier",
                "source": "bottom",
                "sourceNode": "operator-6",
                "target": "top",
                "targetNode": "operator-7",
                "type": "endpoint"
            },
            {
                "arrow": true,
                "arrowPosition": 1,
                "hasRadius": true,
                "shapeType": "AdvancedBezier",
                "source": "bottom",
                "sourceNode": "operator-7",
                "target": "top",
                "targetNode": "operator-8",
                "type": "endpoint"
            }
        ],
        "groups": [],
        "nodes": [
            {
                "endpoints": [
                    {
                        "id": "bottom",
                        "orientation": [
                            0,
                            1
                        ],
                        "pos": [
                            0.5,
                            0.0
                        ]
                    }
                ],
                "id": "operator-1",
                "label": "1_fmdb_default",
                "left": 232,
                "operator": "FmdbDsOperator",
                "status": "unknown",
                "top": 74,
                "type": "dataSource"
            },
            {
                "endpoints": [
                    {
                        "id": "top",
                        "orientation": [
                            0,
                            -1
                        ],
                        "pos": [
                            0.5,
                            0.0
                        ]
                    },
                    {
                        "id": "bottom",
                        "orientation": [
                            0,
                            1
                        ],
                        "pos": [
                            0.5,
                            0.0
                        ]
                    }
                ],
                "id": "operator-3",
                "label": "3_from massdata.DWS_PER_NUL_HIS_CONTACT",
                "left": 232,
                "operator": "FromOperator",
                "status": "unknown",
                "top": 148,
                "type": "table"
            },
            {
                "endpoints": [
                    {
                        "id": "top",
                        "orientation": [
                            0,
                            -1
                        ],
                        "pos": [
                            0.5,
                            0.0
                        ]
                    },
                    {
                        "id": "bottom",
                        "orientation": [
                            0,
                            1
                        ],
                        "pos": [
                            0.5,
                            0.0
                        ]
                    }
                ],
                "id": "operator-4",
                "label": "4_mapping",
                "left": 232,
                "operator": "MappingOperator",
                "status": "unknown",
                "top": 222,
                "type": "unknown"
            },
            {
                "endpoints": [
                    {
                        "id": "top",
                        "orientation": [
                            0,
                            -1
                        ],
                        "pos": [
                            0.5,
                            0.0
                        ]
                    },
                    {
                        "id": "bottom",
                        "orientation": [
                            0,
                            1
                        ],
                        "pos": [
                            0.5,
                            0.0
                        ]
                    }
                ],
                "id": "operator-5",
                "label": "5_where",
                "left": 232,
                "operator": "WhereOperator",
                "status": "unknown",
                "top": 296,
                "type": "unknown"
            },
            {
                "endpoints": [
                    {
                        "id": "top",
                        "orientation": [
                            0,
                            -1
                        ],
                        "pos": [
                            0.5,
                            0.0
                        ]
                    },
                    {
                        "id": "bottom",
                        "orientation": [
                            0,
                            1
                        ],
                        "pos": [
                            0.5,
                            0.0
                        ]
                    }
                ],
                "id": "operator-6",
                "label": "6_select",
                "left": 232,
                "operator": "SelectOperator",
                "status": "unknown",
                "top": 370,
                "type": "unknown"
            },
            {
                "endpoints": [
                    {
                        "id": "top",
                        "orientation": [
                            0,
                            -1
                        ],
                        "pos": [
                            0.5,
                            0.0
                        ]
                    },
                    {
                        "id": "bottom",
                        "orientation": [
                            0,
                            1
                        ],
                        "pos": [
                            0.5,
                            0.0
                        ]
                    }
                ],
                "id": "operator-7",
                "label": "7_distinct",
                "left": 232,
                "operator": "DistinctOperator",
                "status": "unknown",
                "top": 444,
                "type": "unknown"
            },
            {
                "endpoints": [
                    {
                        "id": "top",
                        "orientation": [
                            0,
                            -1
                        ],
                        "pos": [
                            0.5,
                            0.0
                        ]
                    }
                ],
                "id": "operator-8",
                "label": "8_insertTo tre_temp_result_90310af8c4514d869acf8ad149c0e958_1773824289",
                "left": 232,
                "operator": "ToOperator",
                "status": "unknown",
                "top": 518,
                "type": "table"
            }
        ]
    }
}

```

