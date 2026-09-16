### 表初始化逻辑
初始化脚本存放包路径 org/camunda/bpm/engine/db

执行表初始化 AbstractPersistenceSession.dbSchemaUpdate

### 表结构查询
#### 资源相关表
| 分类       | 表名称                     | 说明          |
|----------|-------------------------|-------------|
| ACT_RE_* | act_re_camformdef       | 表单定义        |
| ACT_RE_* | act_re_case_def         | 案例管理模型定义表   |
| ACT_RE_* | act_re_decision_def     | DMN决策模型定义表  |
| ACT_RE_* | act_re_decision_req_def | 决策请求定义表     |
| ACT_RE_* | act_re_deployment       | 流程部署表       |
| ACT_RE_* | act_re_procdef          | BPMN流程模型定义表 |

#### 运行时相关表

| 分类       | 表名称                     | 说明         |
|----------|-------------------------|------------|
| ACT_RU_* | act_ru_authorization    | 流程运行时收取�   |
| ACT_RU_* | act_ru_batch            | 流程执行批处理表   |
| ACT_RU_* | act_ru_case_execution   | 案例运行执行表    |
| ACT_RU_* | act_ru_case_sentry_part | 案例运行哨兵表    |
| ACT_RU_* | act_ru_event_subscr     | 流程事件订阅表    |
| ACT_RU_* | act_ru_execution        | 流程运行时记录表   |
| ACT_RU_* | act_ru_task             | 流程运行时任务表   |
| ACT_RU_* | act_ru_ext_task         | 流程运行时外部任务表 |
| ACT_RU_* | act_ru_task_meter_log   | 运行任务计量日志   |
| ACT_RU_* | act_ru_filter           | 流程定义查询配置表  |
| ACT_RU_* | act_ru_identitylink     | 运行时流程人员表   |
| ACT_RU_* | act_ru_incident         | 运行时异常事件表   |
| ACT_RU_* | act_ru_job              | 流程运行时作业表   |
| ACT_RU_* | act_ru_jobdef           | 流程作业定义表    |
| ACT_RU_* | act_ru_meter_log        | 流程运行时度量日志表 |
| ACT_RU_* | act_ru_variable         | 流程运行时变量表   |

#### 用户、组相关表
| 分类       | 表名称                  | 说明      |
|----------|----------------------|---------|
| ACT_ID_* | act_id_group         | 群组信息表   |
| ACT_ID_* | act_id_info          | 用户扩展信息表 |
| ACT_ID_* | act_id_membership    | 用户群组关系表 |
| ACT_ID_* | act_id_tenant        | 租户信息表   |
| ACT_ID_* | act_id_tenant_member | 用户租户关系表 |
| ACT_ID_* | act_id_user          | 用户信息表   |

#### 历史数据相关表
| 分类       | 表名称                 | 说明              |
|----------|---------------------|-----------------|
| ACT_HI_* | act_hi_actinst      | 历史的活动实例表        |
| ACT_HI_* | act_hi_attachment   | 历史的流程附件表        |
| ACT_HI_* | act_hi_batch        | 历史的批处理记录表       |
| ACT_HI_* | act_hi_caseactinst  | 历史的CMMN活动实例表    |
| ACT_HI_* | act_hi_caseinst     | 历史的CMMN实例表      |
| ACT_HI_* | act_hi_comment      | 历史的流程审批意见表      |
| ACT_HI_* | act_hi_dec_in       | 历史的DMN变量输入表     |
| ACT_HI_* | act_hi_dec_out      | 历史的DMN变量输出表     |
| ACT_HI_* | act_hi_decinst      | 历史的DMN实例表       |
| ACT_HI_* | act_hi_detail       | 历史的流程运行时变量详情记录表 |
| ACT_HI_* | act_hi_ext_task_log | 历史的流程任务消息执行表    |
| ACT_HI_* | act_hi_identitylink | 历史的流程运行过程中用户关系  |
| ACT_HI_* | act_hi_incident     | 历史的流程异常事件记录表    |
| ACT_HI_* | act_hi_job_log      | 历史的流程作业记录表      |
| ACT_HI_* | act_hi_op_log       | 历史操作日志          |
| ACT_HI_* | act_hi_procinst     | 历史的流程实例         |
| ACT_HI_* | act_hi_taskinst     | 历史的任务实例         |
| ACT_HI_* | act_hi_varinst      | 历史的流程变量记录表      |


#### 通用数据相关表
| 分类       | 表名称               | 说明         |
|----------|-------------------|------------|
| ACT_GE_* | act_ge_bytearray  | 流程引擎二进制数据表 |
| ACT_GE_* | act_ge_property   | 流程引擎属性配置表  |
| ACT_GE_* | act_ge_schema_log | 数据库脚本执行日志表 |

### 具体表说明
#### act_ge_bytearray(二进制数据表)
| 字段名称               | 字段类型         | 可否为空 | 说明                     |
|--------------------|--------------|------|------------------------|
| ID_                | varchar(64)  | 否    | 主键                     |
| REV_               | int(11)      | 是    | 版本                     |
| NAME_              | varchar(255) | 是    | 名称                     |
| DEPLOYMENT_ID_     | varchar(64)  | 是    | 部署ID                   |
| BYTES_             | longblob     | 是    | 字节内容                   |
| GENERATED_         | tinyint(4)   | 是    | 是否系统生成(0用户创建，null系统生成) |
| TENANT_ID_         | varchar(64)  | 是    | 租户ID                   |
| TYPE_              | int(11)      | 是    | 类型                     |
| CREATE_TIME_       | datetime     | 是    | 创建时间                   |
| ROOT_PROC_INST_ID_ | varchar(64)  | 是    | 流程实例根ID                |
| REMOVAL_TIME_      | datetime     | 是    | 删除时间                   |


#### act_ge_property(流程引擎配置表)
| 字段名称   | 字段类型         | 可否为空 | 说明 |
|--------|--------------|------|----|
| NAME_  | varchar(64)  | 否    | 名称 |
| VALUE_ | varchar(300) | 是    | 值  |
| REV_   | int(11)      | 是    | 版本 |

#### act_ge_schema_log(数据库脚本执行日志表)
| 字段名称       | 字段类型         | 可否为空 | 说明  |
|------------|--------------|------|-----|
| ID_        | varchar(64)  | 否    | 主键  |
| TIMESTAMP_ | datetime     | 是    | 时间戳 |
| VERSION_   | varchar(255) | 是    | 版本  |

#### act_hi_actinst(历史的活动实例表)

| 字段名称                | 字段类型         | 可否为空 | 说明          |
|---------------------|--------------|------|-------------|
| ID_                 | varchar(64)  | 否    | 主键          |
| PARENT_ACT_INST_ID_ | varchar(64)  | 是    | 父节点实例ID     |
| PROC_DEF_KEY_       | varchar(255) | 是    | 流程定义KEY     |
| PROC_DEF_ID_        | varchar(64)  | 否    | 流程定义ID      |
| ROOT_PROC_INST_ID_  | varchar(64)  | 是    | 流程实例根ID     |
| PROC_INST_ID_       | varchar(64)  | 否    | 流程实例ID      |
| EXECUTION_ID_       | varchar(64)  | 否    | 执行实例ID      |
| ACT_ID_             | varchar(255) | 否    | 节点ID        |
| TASK_ID_            | varchar(64)  | 是    | 任务ID        |
| CALL_PROC_INST_ID_  | varchar(64)  | 是    | 调用外部的流程实例ID |
| CALL_CASE_INST_ID_  | varchar(64)  | 是    | 调用外部的案例实例ID |
| ACT_NAME_           | varchar(255) | 是    | 节点名称        |
| ACT_TYPE_           | varchar(255) | 否    | 节点类型        |
| ASSIGNEE_           | varchar(64)  | 是    | 办理人         |
| START_TIME_         | datetime     | 否    | 开始时间        |
| END_TIME_           | datetime     | 是    | 结束时间        |
| DURATION_           | bigint(20)   | 是    | 耗时          |
| ACT_INST_STATE_     | int(11)      | 是    | 活动实例状态      |
| SEQUENCE_COUNTER_   | bigint(20)   | 是    | 序列计数器       |
| TENANT_ID_          | varchar(64)  | 是    | 租户ID        |
| REMOVAL_TIME_       | datetime     | 是    | 删除时间        |


#### act_hi_comment(历史流程审批意见表)
| 字段名称               | 字段类型          | 可否为空 | 说明                    |
|--------------------|---------------|------|-----------------------|
| ID_                | varchar(64)   | 否    | 主键                    |
| TYPE_              | varchar(255)  | 是    | 类型(event事件、comment意见) |
| TIME_              | datetime      | 否    | 时间                    |
| USER_ID_           | varchar(255)  | 是    | 处理人                   |
| TASK_ID_           | varchar(64)   | 是    | 任务ID                  |
| ROOT_PROC_INST_ID_ | varchar(64)   | 是    | 流程实例跟ID               |
| PROC_INST_ID_      | varchar(64)   | 是    | 流程实例ID                |
| ACTION_            | varchar(255)  | 是    | 行为类型                  |
| MESSAGE_           | varchar(4000) | 是    | 基本内容                  |
| FULL_MSG_          | longblob      | 是    | 全部内容                  |
| TENANT_ID_         | varchar(64)   | 是    | 租户ID                  |
| REMOVAL_TIME_      | datetime      | 是    | 删除时间                  |

#### act_hi_detail(历史的流程运行详情表)
| 字段名称               | 字段类型          | 可否为空 | 说明        |
|--------------------|---------------|------|-----------|
| ID_                | varchar(64)   | 否    | 主键        |
| TYPE_              | varchar(255)  | 否    | 类型        |
| PROC_DEF_KEY_      | varchar(255)  | 是    | 流程定义KEY   |
| PROC_DEF_ID_       | varchar(64)   | 是    | 流程定义ID    |
| ROOT_PROC_INST_ID_ | varchar(64)   | 是    | 流程实例根ID   |
| PROC_INST_ID_      | varchar(64)   | 是    | 流程实例ID    |
| EXECUTION_ID_      | varchar(64)   | 是    | 流程执行ID    |
| CASE_DEF_KEY_      | varchar(255)  | 是    | 案例定义KEY   |
| CASE_DEF_ID_       | varchar(64)   | 是    | 案例定义ID    |
| CASE_INST_ID_      | varchar(64)   | 是    | 案例实例ID    |
| CASE_EXECUTION_ID_ | varchar(64)   | 是    | 案例执行ID    |
| TASK_ID_           | varchar(64)   | 是    | 任务ID      |
| ACT_INST_ID_       | varchar(64)   | 是    | 节点实例ID    |
| VAR_INST_ID_       | varchar(64)   | 是    | 流程变量记录ID  |
| NAME_              | varchar(255)  | 否    | 名称        |
| VAR_TYPE_          | varchar(255)  | 是    | 变量类型      |
| REV_               | int(11)       | 是    | 版本        |
| TIME_              | datetime      | 否    | 时间戳       |
| BYTEARRAY_ID_      | varchar(64)   | 是    | 二进制数据对应ID |
| DOUBLE_            | double        | 是    | double类型值 |
| LONG_              | bigint(20)    | 是    | long类型值   |
| TEXT_              | varchar(4000) | 是    | 文本类型值     |
| TEXT2_             | varchar(4000) | 是    | 文本类型值2    |
| SEQUENCE_COUNTER_  | bigint(20)    | 是    | 序列计数器     |
| TENANT_ID_         | varchar(64)   | 是    | 租户ID      |
| OPERATION_ID_      | varchar(64)   | 是    | 操作id      |
| REMOVAL_TIME_      | datetime      | 是    | 删除时间      |


#### act_hi_identitylink(历史的流程运行过程中用户表)
| 字段名称               | 字段类型         | 可否为空 | 说明      |
|--------------------|--------------|------|---------|
| ID_                | varchar(64)  | 否    | 主键      |
| TIMESTAMP_         | timestamp    | 否    | 时间戳     |
| TYPE_              | varchar(255) | 是    | 类型      |
| USER_ID_           | varchar(255) | 是    | 用户ID    |
| GROUP_ID_          | varchar(255) | 是    | 用户组ID   |
| TASK_ID_           | varchar(64)  | 是    | 任务ID    |
| ROOT_PROC_INST_ID_ | varchar(64)  | 是    | 流程实例根ID |
| PROC_DEF_ID_       | varchar(64)  | 是    | 流程定义ID  |
| OPERATION_TYPE_    | varchar(64)  | 是    | 操作类型    |
| ASSIGNER_ID_       | varchar(64)  | 是    | 分配者ID   |
| PROC_DEF_KEY_      | varchar(255) | 是    | 流程定义KEY |
| TENANT_ID_         | varchar(64)  | 是    | 租户ID    |
| REMOVAL_TIME_      | datetime     | 是    | 删除时间    |

#### act_hi_procinst(历史的流程实例表)
| 字段名称                       | 字段类型          | 可否为空 | 说明      |
|----------------------------|---------------|------|---------|
| ID_                        | varchar(64)   | 否    | 主键      |
| PROC_INST_ID_              | varchar(64)   | 否    | 流程实例ID  |
| BUSINESS_KEY_              | varchar(255)  | 是    | 业务KEY   |
| PROC_DEF_KEY_              | varchar(255)  | 是    | 流程定义KEY |
| PROC_DEF_ID_               | varchar(64)   | 否    | 流程定义ID  |
| START_TIME_                | datetime      | 否    | 开始时间    |
| END_TIME_                  | datetime      | 是    | 结束时间    |
| REMOVAL_TIME_              | datetime      | 是    | 删除时间    |
| DURATION_                  | bigint(20)    | 是    | 耗时      |
| START_USER_ID_             | varchar(255)  | 是    | 启动人ID   |
| START_ACT_ID_              | varchar(255)  | 是    | 启动节点ID  |
| END_ACT_ID_                | varchar(255)  | 是    | 结束节点ID  |
| SUPER_PROCESS_INSTANCE_ID_ | varchar(64)   | 是    | 父流程实例ID |
| ROOT_PROC_INST_ID_         | varchar(64)   | 是    | 流程实例根ID |
| SUPER_CASE_INSTANCE_ID_    | varchar(64)   | 是    | 父案例实例ID |
| CASE_INST_ID_              | varchar(64)   | 是    | 案例实例ID  |
| DELETE_REASON_             | varchar(4000) | 是    | 删除原因    |
| TENANT_ID_                 | varchar(64)   | 是    | 租户ID    |
| STATE_                     | varchar(255)  | 是    | 状态      |

#### act_hi_taskinst(历史的任务实例表)
| 字段名称               | 字段类型          | 可否为空 | 说明      |
|--------------------|---------------|------|---------|
| ID_                | varchar(64)   | 否    | 主键      |
| TASK_DEF_KEY_      | varchar(255)  | 是    | 任务定义KEY |
| PROC_DEF_KEY_      | varchar(255)  | 是    | 流程定义KEY |
| PROC_DEF_ID_       | varchar(64)   | 是    | 流程定义ID  |
| ROOT_PROC_INST_ID_ | varchar(64)   | 是    | 流程实例根ID |
| PROC_INST_ID_      | varchar(64)   | 是    | 流程实例ID  |
| EXECUTION_ID_      | varchar(64)   | 是    | 流程执行ID  |
| CASE_DEF_KEY_      | varchar(255)  | 是    | 案例定义KEY |
| CASE_DEF_ID_       | varchar(64)   | 是    | 案例定义ID  |
| CASE_INST_ID_      | varchar(64)   | 是    | 案例实例ID  |
| CASE_EXECUTION_ID_ | varchar(64)   | 是    | 案例执行ID  |
| ACT_INST_ID_       | varchar(64)   | 是    | 节点实例ID  |
| NAME_              | varchar(255)  | 是    | 名称      |
| PARENT_TASK_ID_    | varchar(64)   | 是    | 父任务ID   |
| DESCRIPTION_       | varchar(4000) | 是    | 说明      |
| OWNER_             | varchar(255)  | 是    | 委托人ID   |
| ASSIGNEE_          | varchar(255)  | 是    | 办理人ID   |
| START_TIME_        | datetime      | 否    | 开始时间    |
| END_TIME_          | datetime      | 是    | 结束时间    |
| DURATION_          | bigint(20)    | 是    | 耗时      |
| DELETE_REASON_     | varchar(4000) | 是    | 删除原因    |
| PRIORITY_          | int(11)       | 是    | 优先级     |
| DUE_DATE_          | datetime      | 是    | 超时时间    |
| FOLLOW_UP DATE     | datetime      | 是    | 跟踪时间    |
| TENANT_ID_         | varchar(64)   | 是    | 租户ID    |
| REMOVAL_TIME_      | datetime      | 是    | 删除时间    |

#### act_hi_varinst(历史的流程变量表)
| 字段名称               | 字段类型          | 可否为空 | 说明        |
|--------------------|---------------|------|-----------|
| ID_                | varchar(64)   | 否    | 主键        |
| PROC_DEF_KEY_      | varchar(255)  | 是    | 流程定义KEY   |
| PROC_DEF_ID_       | varchar(64)   | 是    | 流程定义ID    |
| ROOT_PROC_INST_ID_ | varchar(64)   | 是    | 流程实例根ID   |
| PROC_INST_ID_      | varchar(64)   | 是    | 流程实例ID    |
| EXECUTION_ID_      | varchar(64)   | 是    | 流程执行ID    |
| ACT_INST_ID_       | varchar(64)   | 是    | 节点实例ID    |
| CASE_DEF_KEY_      | varchar(255)  | 是    | 案例定义KEY   |
| CASE_DEF_ID_       | varchar(64)   | 是    | 案例定义ID    |
| CASE_INST_ID_      | varchar(64)   | 是    | 案例实例ID    |
| CASE_EXECUTION_ID_ | varchar(64)   | 是    | 案例执行ID    |
| TASK_ID_           | varchar(64)   | 是    | 任务ID      |
| NAME_              | varchar(255)  | 否    | 名称        |
| VAR_TYPE_          | varchar(100)  | 是    | 变量类型      |
| CREATE_TIME_       | datetime      | 是    | 创建时间      |
| REV_               | int(11)       | 是    | 版本        |
| BYTEARRAY_ID_      | varchar(64)   | 是    | 二进制数据ID   |
| DOUBLE_            | double        | 是    | double类型值 |
| LONG_              | bigint(20)    | 是    | long类型值   |
| TEXT_              | varchar(4000) | 是    | 文本类型值     |
| TEXT2_             | varchar(4000) | 是    | 文本类型值2    |
| TENANT_ID_         | varchar(64)   | 是    | 租户ID      |
| STATE_             | varchar(20)   | 是    | 状态        |
| REMOVAL_TIME_      | datetime      | 是    | 删除时间      |


#### act_id_user(用户表)
| 字段名称           | 字段类型         | 可否为空 | 说明     |
|----------------|--------------|------|--------|
| ID_            | varchar(64)  | 否    | 主键     |
| REV_           | int(11)      | 是    | 版本     |
| FIRST_         | varchar(255) | 是    | 姓      |
| LAST_          | varchar(255) | 是    | 名      |
| EMAIL_         | varchar(255) | 是    | 邮件     |
| PWD_           | varchar(255) | 是    | 密码     |
| SALT_          | varchar(255) | 是    | 盐值     |
| LOCK_EXP_TIME_ | datetime     | 是    | 锁定过期时间 |
| ATTEMPTS_      | int(11)      | 是    | 尝试次数   |
| PICTURE_ID_    | varchar(64)  | 是    | 图片ID   |

#### act_id_group(群组表)
| 字段名称  | 字段类型         | 可否为空 | 说明                       |
|-------|--------------|------|--------------------------|
| ID_   | varchar(64)  | 否    | 主键                       |
| REV_  | int(11)      | 是    | 版本                       |
| NAME_ | varchar(255) | 是    | 组名称                      |
| TYPE_ | varchar(255) | 是    | 组类型(SYSTEM系统、WORKFLOW业务) |

#### act_id_membership(用户与群组关系表)
| 字段名称      | 字段类型        | 可否为空 | 说明   |
|-----------|-------------|------|------|
| USER_ID_  | varchar(64) | 否    | 用户ID |
| GROUP_ID_ | varchar(64) | 否    | 组ID  |

#### act_re_deployment(流程部署表)
| 字段名称         | 字段类型         | 可否为空 | 说明   |
|--------------|--------------|------|------|
| ID_          | varchar(64)  | 否    | 主键   |
| NAME_        | varchar(255) | 是    | 流程名称 |
| DEPLOY_TIME_ | datetime     | 是    | 部署时间 |
| SOURCE_      | varchar(255) | 是    | 来源   |
| TENANT_ID_   | varchar(64)  | 是    | 租户ID |

#### act_re_procdef(流程定义表)
| 字段名称                | 字段类型          | 可否为空 | 说明               |
|---------------------|---------------|------|------------------|
| ID_                 | varchar(64)   | 否    | 主键               |
| REV_                | int(11)       | 是    | 版本               |
| CATEGORY_           | varchar(255)  | 是    | 流程定义的Namespace分类 |
| NAME_               | varchar(255)  | 是    | 流程定义名称           |
| KEY_                | varchar(255)  | 否    | 流程定义KEY          |
| VERSION_            | int(11)       | 否    | 流程定义版本号          |
| DEPLOYMENT_ID_      | varchar(64)   | 是    | 部署ID             |
| RESOURCE_NAME_      | varchar(4000) | 是    | 资源名称             |
| DGRM_RESOURCE_NAME_ | varchar(4000) | 是    | DGRM资源名称         |
| HAS_START_FORM_KEY_ | tinyint(4)    | 是    | 是否有启动表单          |
| SUSPENSION_STATE_   | int(11)       | 是    | 流程挂起             |
| TENANT_ID_          | varchar(64)   | 是    | 租户ID             |
| VERSION_TAG_        | varchar(64)   | 是    | 版本标签             |
| HISTORY_TTL_        | int(11)       | 是    |                  |
| STARTABLE_          | tinyint(1)    | 否    | 是否是可启动流程         |

#### act_ru_ext_task(运行时外部任务表)
| 列名                | 数据类型          | 能否为空 | 说明         |
|-------------------|---------------|------|------------|
| ID_               | varchar(64)   | 否    | 主键         |
| REV_              | int(11)       | 否    | 版本         |
| WORKER_ID_        | varchar(255)  | 是    | workerID   |
| TOPIC_NAME_       | varchar(255)  | 是    | 主题         |
| RETRIES_          | int(11)       | 0    | 是          |
| ERROR_MSG_        | varchar(4000) | 是    | 错误信息       |
| ERROR_DETAILS_ID_ | varchar(64)   | 是    | 错误详情ID     |
| LOCK_EXP_TIME_    | datetime      | 0    | 是          |
| SUSPENSION_STATE_ | int(11)       | 是    | 挂起状态，1表示激活 |
| EXECUTION_ID_     | varchar(64)   | 是    | 执行ID       |
| PROC_INST_ID_     | varchar(64)   | 是    | 流程实例ID     |
| PROC_DEF_ID_      | varchar(64)   | 是    | 流程定义ID     |
| PROC_DEF_KEY_     | varchar(255)  | 是    | 流程定义key    |
| ACT_ID_           | varchar(255)  | 是    | 外部任务ID     |
| ACT_INST_ID_      | varchar(64)   | 是    | 外部任务实例ID   |
| TENANT_ID_        | varchar(64)   | 是    | 租户ID       |
| PRIORITY_         | bigint(20)    | 否    | 优先级        |

#### act_ru_event_subscr(流程事件订阅表)
| 字段名称           | 字段类型         | 可否为空 | 说明     |
|----------------|--------------|------|--------|
| ID_            | varchar(64)  | 否    | 主键     |
| REV_           | int(11)      | 是    | 乐观锁版本  |
| EVENT_TYPE_    | varchar(255) | 否    | 事件类型   |
| EVENT_NAME_    | varchar(255) | 是    | 事件名称   |
| EXECUTION_ID_  | varchar(64)  | 是    | 执行ID   |
| PROC_INST_ID_  | varchar(64)  | 是    | 流程实例ID |
| ACTIVITY_ID_   | varchar(255) | 是    | 节点ID   |
| CONFIGURATION_ | varchar(255) | 是    | 配置     |
| CREATED_       | datetime     | 否    | 创建时间   |
| TENANT_ID_     | varchar(64)  | 是    | 租户ID   |

#### act_ru_execution(流程运行时表)
| 字段名称               | 字段类型         | 可否为空 | 说明         |
|--------------------|--------------|------|------------|
| ID_                | varchar(64)  | 否    | 主键         |
| REV_               | int(11)      | 是    | 版本         |
| ROOT_PROC_INST_ID_ | varchar(64)  | 是    | 流程实例根ID    |
| PROC_INST_ID_      | varchar(64)  | 是    | 流程实例ID     |
| BUSINESS_KEY_      | varchar(255) | 是    | 业务KEY      |
| PARENT_ID_         | varchar(64)  | 是    | 流程父实例ID    |
| PROC_DEF_ID_       | varchar(64)  | 是    | 流程定义ID     |
| SUPER_EXEC_        | varchar(64)  | 是    | 父流程实例对应的执行 |
| SUPER_CASE_EXEC_   | varchar(64)  | 是    | 父案例实例对应的执行 |
| CASE_INST_ID_      | varchar(64)  | 是    | 实例ID       |
| ACT_ID_            | varchar(255) | 是    | 节点ID       |
| ACT_INST_ID_       | varchar(64)  | 是    | 节点实例ID     |
| IS_ACTIVE_         | tinyint(4)   | 是    | 是否激活       |
| IS_CONCURRENT_     | tinyint(4)   | 是    | 是否并行       |
| IS_SCOPE_          | tinyint(4)   | 是    | 是否多实例范围    |
| IS_EVENT_SCOPE_    | tinyint(4)   | 是    | 是否事件多实例范围  |
| SUSPENSION_STATE_  | int(11)      | 是    | 挂起状态       |
| CACHED_ENT_STATE_  | int(11)      | 是    | 缓存状态       |
| SEQUENCE_COUNTER_  | bigint(20)   | 是    | 序列计数器      |
| TENANT_ID_         | varchar(64)  | 是    | 租户ID       |

#### act_ru_identitylink(流程运行时表)
| 字段名称         | 字段类型         | 可否为空 | 说明     |
|--------------|--------------|------|--------|
| ID_          | varchar(64)  | 否    | 主键     |
| REV_         | int(11)      | 是    | 版本     |
| GROUP_ID_    | varchar(255) | 是    | 用户组ID  |
| TYPE_        | varchar(255) | 是    | 类型     |
| USER_ID_     | varchar(255) | 是    | 用户ID   |
| TASK_ID_     | varchar(64)  | 是    | 任务ID   |
| PROC_DEF_ID_ | varchar(64)  | 是    | 流程定义ID |
| TENANT_ID_   | varchar(64)  | 是    | 租户ID   |

#### act_ru_incident(运行时异常记录表)
| 字段名称                    | 字段类型          | 可否为空 | 说明      |
|-------------------------|---------------|------|---------|
| ID_                     | varchar(64)   | 否    | 主键      |
| REV_                    | int(11)       | 否    | 版本      |
| INCIDENT_TIMESTAMP_     | datetime      | 否    | 事件时间戳   |
| INCIDENT_MSG_           | varchar(4000) | 是    | 事件信息    |
| INCIDENT_TYPE_          | varchar(255)  | 否    | 事件类型    |
| EXECUTION_ID_           | varchar(64)   | 是    | 流程执行ID  |
| ACTIVITY_ID_            | varchar(255)  | 是    | 节点ID    |
| PROC_INST_ID_           | varchar(64)   | 是    | 流程实例ID  |
| PROC_DEF_ID_            | varchar(64)   | 是    | 流程定义ID  |
| CAUSE_INCIDENT_ID_      | varchar(64)   | 是    | 事件原因ID  |
| ROOT_CAUSE_INCIDENT_ID_ | varchar(64)   | 是    | 事件原因根ID |
| CONFIGURATION_          | varchar(255)  | 是    | 事件配置    |
| TENANT_ID_              | varchar(64)   | 是    | 租户ID    |
| JOB_DEF_ID_             | varchar(64)   | 是    | JOB定义ID |

#### act_ru_job(流程运行时作业表)
| 字段名称                 | 字段类型          | 可否为空 | 说明      |
|----------------------|---------------|------|---------|
| ID_                  | varchar(64)   | 否    | 主键      |
| REV_                 | int(11)       | 是    | 版本      |
| TYPE_                | varchar(255)  | 否    | 类型      |
| LOCK_EXP_TIME_       | datetime      | 是    | 锁定过期时间  |
| LOCK_OWNER_          | varchar(255)  | 是    | 锁定用户    |
| EXCLUSIVE_           | tinyint(1)    | 是    | 独占标识    |
| EXECUTION_ID_        | varchar(64)   | 是    | 执行ID    |
| PROCESS_INSTANCE_ID_ | varchar(64)   | 是    | 流程实例ID  |
| PROCESS_DEF_ID_      | varchar(64)   | 是    | 流程定义ID  |
| PROCESS_DEF_KEY_     | varchar(255)  | 是    | 流程定义KEY |
| RETRIES_             | int(11)       | 是    | 重试次数    |
| EXCEPTION_STACK_ID_  | varchar(64)   | 是    | 异常堆栈ID  |
| EXCEPTION_MSG_       | varchar(4000) | 是    | 异常信息    |
| DUEDATE_             | datetime      | 是    | 截止时间    |
| REPEAT_              | varchar(255)  | 是    | 重复      |
| REPEAT_OFFSET_       | bigint(20)    | 是    |         |
| HANDLER_TYPE_        | varchar(255)  | 是    | 处理类型    |
| HANDLER_CFG_         | varchar(4000) | 是    | 处理配置    |
| DEPLOYMENT_ID_       | varchar(64)   | 是    | 部署ID    |
| SUSPENSION_STATE_    | int(11)       | 否    | 挂起状态    |
| JOB_DEF_ID_          | varchar(64)   | 是    | 作业定义ID  |
| PRIORITY_            | bigint(20)    | 否    | 作业优先级   |
| SEQUENCE_COUNTER_    | bigint(20)    | 是    | 序列计数器   |
| TENANT_ID_           | varchar(64)   | 是    | 租户ID    |
| CREATE_TIME          | _ datetime    | 是    | 创建时间    |

#### act_ru_jobdef(流程作业定义表)
| 字段名称               | 字段类型         | 可否为空 | 说明      |
|--------------------|--------------|------|---------|
| ID_                | varchar(64)  | 否    | 主键      |
| REV_               | int(11)      | 是    | 版本      |
| PROC_DEF_ID_       | varchar(64)  | 是    | 流程定义ID  |
| PROC_DEF_KEY_      | varchar(255) | 是    | 流程定义KEY |
| ACT_ID_            | varchar(255) | 是    | 节点ID    |
| JOB_TYPE_          | varchar(255) | 否    | JOB类型   |
| JOB_CONFIGURATION_ | varchar(255) | 是    | JOB配置   |
| SUSPENSION_STATE_  | int(11)      | 是    | 挂起状态    |
| JOB_PRIORITY_      | bigint(20)   | 是    | 优先级     |
| TENANT_ID_         | varchar(64)  | 是    | 租户ID    |

#### act_ru_task(流程运行时任务表)
| 字段名称               | 字段类型          | 可否为空 | 说明      |
|--------------------|---------------|------|---------|
| ID_                | varchar(64)   | 否    | 主键      |
| REV_               | int(11)       | 是    | 版本      |
| EXECUTION_ID_      | varchar(64)   | 是    | 流程执行ID  |
| PROC_INST_ID_      | varchar(64)   | 是    | 流程实例ID  |
| PROC_DEF_ID_       | varchar(64)   | 是    | 流程定义ID  |
| CASE_EXECUTION_ID_ | varchar(64)   | 是    | 案例执行ID  |
| CASE_INST_ID_      | varchar(64)   | 是    | 案例实例ID  |
| CASE_DEF_ID_       | varchar(64)   | 是    | 案例定义ID  |
| NAME_              | varchar(255)  | 是    | 名称      |
| PARENT_TASK_ID_    | varchar(64)   | 是    | 父任务ID   |
| DESCRIPTION_       | varchar(4000) | 是    | 说明      |
| TASK_DEF_KEY_      | varchar(255)  | 是    | 任务定义KEY |
| OWNER_             | varchar(255)  | 是    | 委托人     |
| ASSIGNEE_          | varchar(255)  | 是    | 办理人     |
| DELEGATION_        | varchar(64)   | 是    | 委托状态    |
| PRIORITY_          | int(11)       | 是    | 优先级     |
| CREATE_TIME_       | datetime      | 是    | 创建时间    |
| DUE_DATE_          | datetime      | 是    | 截止时间    |
| FOLLOW_UP_DATE_    | datetime      | 是    | 跟踪时间    |
| SUSPENSION_STATE_  | int(11)       | 是    | 挂起状态    |
| TENANT_ID_         | varchar(64)   | 是    | 租户ID    |

#### act_ru_variable(流程运行时变量表)
| 字段名称                  | 字段类型          | 可否为空 | 说明        |
|-----------------------|---------------|------|-----------|
| ID_                   | varchar(64)   | 否    | 主键        |
| REV_                  | int(11)       | 是    | 版本        |
| TYPE_                 | varchar(255)  | 否    | 变量类型      |
| NAME_                 | varchar(255)  | 否    | 变量名称      |
| EXECUTION_ID_         | varchar(64)   | 是    | 流程执行ID    |
| PROC_INST_ID_         | varchar(64)   | 是    | 流程实例ID    |
| CASE_EXECUTION_ID_    | varchar(64)   | 是    | 案例执行ID    |
| CASE_INST_ID_         | varchar(64)   | 是    | 案例实例ID    |
| TASK_ID_              | varchar(64)   | 是    | 任务ID      |
| BYTEARRAY_ID_         | varchar(64)   | 是    | 二进制内容ID   |
| DOUBLE_               | double        | 是    | DOUBLE类型值 |
| LONG_                 | bigint(20)    | 是    | LONG类型值   |
| TEXT_                 | varchar(4000) | 是    | 文本值       |
| TEXT2_                | varchar(4000) | 是    | 文本值２      |
| VAR_SCOPE_            | varchar(64)   | 否    | 变量范围      |
| SEQUENCE_COUNTER_     | bigint(20)    | 是    | 序列计数器     |
| IS_CONCURRENT_LOCAL _ | tinyint(4)    | 是    | 是否并发      |
| TENANT_ID_            | varchar(64)   | 是    | 租户IDD     |

```sql
select * from ACT_RU_AUTHORIZATION       t;
select * from ACT_RU_BATCH               t;
select * from ACT_RU_CASE_EXECUTION      t;
select * from ACT_RU_CASE_SENTRY_PART    t;
select * from ACT_RU_EVENT_SUBSCR        t;
select * from ACT_RU_EXECUTION           t;
select * from ACT_RU_EXT_TASK            t;
select * from ACT_RU_FILTER              t;
select * from ACT_RU_IDENTITYLINK        t;
select * from ACT_RU_INCIDENT            t;
select * from ACT_RU_JOB                 t;
select * from ACT_RU_JOBDEF              t;
select * from ACT_RU_METER_LOG           t;
select * from ACT_RU_TASK                t;
select * from ACT_RU_TASK_METER_LOG      t;
select * from ACT_RU_VARIABLE            t;

select * from  ACT_GE_BYTEARRAY            t;
select * from  ACT_GE_PROPERTY             t;
select * from  ACT_GE_SCHEMA_LOG           t;

select * from  ACT_HI_ACTINST              t;
select * from  ACT_HI_ATTACHMENT           t;
select * from  ACT_HI_BATCH                t;
select * from  ACT_HI_CASEACTINST          t;
select * from  ACT_HI_CASEINST             t;
select * from  ACT_HI_COMMENT              t;
select * from  ACT_HI_DECINST              t;
select * from  ACT_HI_DEC_IN               t;
select * from  ACT_HI_DEC_OUT              t;
select * from  ACT_HI_DETAIL               t;
select * from  ACT_HI_EXT_TASK_LOG         t;
select * from  ACT_HI_IDENTITYLINK         t;
select * from  ACT_HI_INCIDENT             t;
select * from  ACT_HI_JOB_LOG              t;
select * from  ACT_HI_OP_LOG               t;
select * from  ACT_HI_PROCINST             t;
select * from  ACT_HI_TASKINST             t;
select * from  ACT_HI_VARINST              t;

select * from  ACT_ID_GROUP                t;
select * from  ACT_ID_INFO                 t;
select * from  ACT_ID_MEMBERSHIP           t;
select * from  ACT_ID_TENANT               t;
select * from  ACT_ID_TENANT_MEMBER        t;
select * from  ACT_ID_USER                 t;

select * from  ACT_RE_CAMFORMDEF           t;
select * from  ACT_RE_CASE_DEF             t;
select * from  ACT_RE_DECISION_DEF         t;
select * from  ACT_RE_DECISION_REQ_DEF     t;
select * from  ACT_RE_DEPLOYMENT           t;
select * from  ACT_RE_PROCDEF              t;

```