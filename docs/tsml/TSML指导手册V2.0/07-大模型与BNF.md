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



