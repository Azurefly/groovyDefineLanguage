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

