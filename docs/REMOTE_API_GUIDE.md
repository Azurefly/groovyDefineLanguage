# GDL 引擎远程服务调用指南 (Remote Service Integration Guide)

GDL（GroovyDefine Language）引擎提供了完善的远程调用支持，允许第三方服务（无论是 Java 后端、Python 脚本、微服务集群，还是基于大模型的 AI Agent 工具链）轻松接入并调用 GDL 数据建模、分布式 ETL、本体治理与 DAG 流程分析能力。

---

## 一、服务启动方式

在服务器终端执行以下脚本启动远程服务：

```bash
# 启动服务，默认监听 8080 端口，无鉴权
./bin/start-server.sh 8080

# 启动服务，监听指定端口并启用安全 Token 认证
./bin/start-server.sh 8080 my-secret-token-123
```

启动成功后，控制台将输出：
```
=============================================================
 GDL Engine Remote Service running at http://localhost:8080
 HTTP REST API: http://localhost:8080/tre/api/...
 MCP Tool API:  http://localhost:8080/tre/mcp/service
 Health Check:  http://localhost:8080/tre/api/health
 Token Auth:    ENABLED / DISABLED
=============================================================
```

---

## 二、第三方 Java 服务接入方式 (Java SDK)

第三方 Java 服务只需引入 `gdl-server` 或 `gdl-common` 模块，即可使用内置的 `GdlHttpEngineClient` 实现透明 RPC 远程调用。

### 1. 初始化客户端

```java
import com.pl.gdl.server.client.GdlEngineClient;
import com.pl.gdl.server.client.GdlHttpEngineClient;

// 创建远程连接客户端
String serverUrl = "http://192.168.1.100:8080";
String token = "my-secret-token-123"; // 未启用 token 时填 null
GdlEngineClient client = new GdlHttpEngineClient(serverUrl, token);
```

### 2. 远程提交 GDL 脚本计算任务并查询结果

```java
// 编写业务 GDL 脚本
String script = """
    def hiveDs = hive()
    def timeVar = variable("CurrentTimeVar", [timeVarName: "today", timeFormat: "yyyy-MM-dd"])
    def df = from(hiveDs, "dw.t_order")
        .where("dt = '${timeVar.today}' and amount > 500")
        .select("order_id, user_id, amount")
        .sort("amount desc")
        .limit(50)
    returnDf(df)
""";

// 提交任务到远程引擎，支持注入参数（如地域编码 areaCode）
String taskId = client.startTask(script, Map.of("areaCode", "320100"));
System.out.println("任务已提交，TaskId: " + taskId);

// 查询执行结果
TaskResult result = client.getTaskResult(taskId);
System.out.println("任务状态: " + result.getStatus());
```

### 3. 远程注册业务本体与元数据查询

```java
String chatOnto = """
    package v1
    import com.pl.gdl.ontology.model.Ontology
    import com.pl.gdl.ontology.annotation.Table
    import com.pl.gdl.ontology.annotation.Column

    @Table(type="ORC", remarks="企业微信群聊模型")
    class WxChat extends Ontology {
        @Column(remarks="群标识")
        String groupId = "group_id"
        @Column(remarks="消息内容")
        String content = "content"

        WxChat() {
            this.oId = "TS_WX_001"
            this.oName = "企业微信群聊"
            this.oDesc = "企业群聊业务模型"
            this.oAuthor = "SecOps"
            this.oTable = "dw.t_wx_chat"
        }
    }
""";

// 远程注册本体
RegisterRsp regRsp = client.registerOntology(chatOnto);
System.out.println("注册结果: " + regRsp.getMessage());

// 查询远程已注册本体元数据
List<OntoInfoRsp> ontos = client.getOntologies("v1.WxChat", "320100");
for (OntoInfoRsp info : ontos) {
    System.out.println("本体名称: " + info.getOName() + ", 表: " + info.getOTable());
}
```

### 4. 远程提取 DAG 画布拓扑

```java
String dagJson = client.getTsmlToDag(script);
System.out.println("DAG 拓扑 JSON: " + dagJson);
```

---

## 三、第三方通用 HTTP REST API 接口文档

对于非 Java 服务（Python、Go、Node.js、微服务等），可通过标准 HTTP/JSON 接口进行集成。若服务端启用了 Token 认证，需在请求头携带 `tre-token: <your-token>`。

### 1. 服务健康检查

- **请求**：`GET /tre/api/health`
- **说明**：无需鉴权，用于健康探测与服务发现
- **响应**：
```json
{
  "status": "UP",
  "service": "GDL Engine",
  "version": "1.0.0-GA",
  "timestamp": 1789384500000
}
```

---

### 2. 提交 GDL 任务 (startTask)

- **请求**：`POST /tre/api/startTask`
- **Headers**：
  - `Content-Type: application/json`
  - `tre-token: <token>`（可选）
- **请求体 (Body)**：
```json
{
  "code": "def hiveDs = hive()\ndef df = from(hiveDs, 'dw.t_user').where('age > 20').select('id, name')\nreturnDf(df)",
  "params": {
    "areaCode": "320100",
    "batchId": "20260930"
  }
}
```
- **cURL 示例**：
```bash
curl -X POST http://localhost:8080/tre/api/startTask \
     -H "Content-Type: application/json" \
     -H "tre-token: my-secret-token-123" \
     -d '{"code":"def hiveDs = hive()\\ndef df = from(hiveDs, \"t_person\").select(\"id, name\")\\nreturnDf(df)"}'
```
- **响应**：
```json
{
  "code": 0,
  "msg": "任务提交成功",
  "taskId": "c92e92c2a01d4a69b763e0018d998124",
  "status": "SUBMITTED",
  "success": true
}
```

---

### 3. 获取任务结果 (getTaskResult)

- **请求**：`GET /tre/api/getTaskResult?taskId=<taskId>`
- **Headers**：`tre-token: <token>`
- **cURL 示例**：
```bash
curl -X GET "http://localhost:8080/tre/api/getTaskResult?taskId=c92e92c2a01d4a69b763e0018d998124" \
     -H "tre-token: my-secret-token-123"
```
- **响应**：
```json
{
  "code": 0,
  "msg": "操作成功",
  "status": "FINISHED",
  "taskId": "c92e92c2a01d4a69b763e0018d998124",
  "result": [
    {
      "areaCode": "320100",
      "code": "TRE_2000",
      "msg": "成功",
      "data": [
        {"id": "1", "name": "张三", "age": 25},
        {"id": "2", "name": "李四", "age": 30}
      ],
      "metadata": [
        {"columnName": "id", "dataTypeName": "varchar"},
        {"columnName": "name", "dataTypeName": "varchar"},
        {"columnName": "age", "dataTypeName": "int"}
      ]
    }
  ],
  "success": true
}
```

---

### 4. 注册本体 (registerOntology)

- **请求**：`POST /tre/api/registerOntology`
- **请求体 (Body)**：
```json
{
  "gdl": "package v1\nimport com.pl.gdl.ontology.model.Ontology\nclass Order extends Ontology { ... }"
}
```
- **响应**：
```json
{
  "code": 0,
  "msg": "Ontology registered successfully",
  "status": 1,
  "data": {
    "v1.Order": "SUCCESS"
  },
  "success": true
}
```

---

### 5. 查询本体信息 (getOntologies)

- **请求**：`GET /tre/api/getOntologies?fullOntologyNames=v1.WxChat&areaCode=320100`
- **响应**：
```json
{
  "code": 0,
  "msg": "操作成功",
  "data": [
    {
      "OId": "TS_WX_001",
      "OName": "企业微信群聊",
      "ODesc": "企业群聊业务模型",
      "OTable": "dw.t_wx_chat",
      "OAuthor": "SecOps",
      "version": "v1",
      "className": "WxChat",
      "fields": [
        {"fieldName": "groupId", "columnName": "group_id", "remarks": "群标识"},
        {"fieldName": "content", "columnName": "content", "remarks": "消息内容"}
      ]
    }
  ],
  "success": true
}
```

---

### 6. GDL 脚本转 DAG 画布图 (getTsmlToDag)

- **请求**：`POST /tre/api/getTsmlToDag`
- **请求体 (Body)**：
```json
{
  "code": "def hiveDs = hive()\ndef df1 = from(hiveDs, 't1').nodeId('n1')\ndef df2 = df1.select('id').nodeId('n2')\ndf2.to(hiveDs, 't2').nodeId('n3')"
}
```
- **响应**：
```json
{
  "canvas": {
    "nodes": [
      {"id": "n1", "label": "from t1", "operator": "FromOperator", "type": "table"},
      {"id": "n2", "label": "select", "operator": "SelectOperator", "type": "operator"},
      {"id": "n3", "label": "to t2", "operator": "ToOperator", "type": "table"}
    ],
    "edges": [
      {"sourceNode": "n1", "targetNode": "n2", "type": "endpoint", "arrow": true},
      {"sourceNode": "n2", "targetNode": "n3", "type": "endpoint", "arrow": true}
    ]
  }
}
```

---

## 四、Python 第三方服务调用示例

```python
import requests

SERVER_URL = "http://localhost:8080"
HEADERS = {
    "Content-Type": "application/json",
    "tre-token": "my-secret-token-123"
}

# 1. 提交 GDL 计算任务
script = """
def hiveDs = hive()
def df = from(hiveDs, "dw.t_device_log")
    .where("status = 'ERROR'")
    .select("device_id, log_time, error_code")
    .limit(10)
returnDf(df)
"""

resp = requests.post(f"{SERVER_URL}/tre/api/startTask", json={"code": script}, headers=HEADERS)
task_info = resp.json()
print("提交结果:", task_info)
task_id = task_info["taskId"]

# 2. 查询结果
res_resp = requests.get(f"{SERVER_URL}/tre/api/getTaskResult?taskId={task_id}", headers=HEADERS)
print("计算结果:", res_resp.json())
```

---

## 五、AI Agent / 大模型平台集成方式 (MCP 协议)

GDL 引擎原生集成了 **Model Context Protocol (MCP)** 流式 HTTP 接口：`POST /tre/mcp/service`。
任何支持 MCP 的平台（CherryStudio、Claude Desktop、Dify、Coze、LangChain 等）都可以直接将其配置为 MCP Server 工具源：

### 1. 配置 MCP Server 地址
- **URL**：`http://[IP]:[PORT]/tre/mcp/service`
- **Transport**：`Streamable HTTP` / `JSON-RPC`
- **Headers**：`{"tre-token": "my-secret-token-123"}`

### 2. 支持的 MCP 工具列表
1. `start_task`：接收大模型生成的 GDL 脚本，调度执行。
2. `get_task_result`：根据 taskId 获取执行详情与数据表格。
3. `get_tsml_to_dag`：将 GML 脚本转为可视化 DAG 图点线边拓扑，供前端画布动态渲染展示（工具名为历史命名，保持兼容）。
