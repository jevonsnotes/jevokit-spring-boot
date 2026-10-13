package io.github.jevonsnotes.jevokit.audit.log.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import io.github.jevonsnotes.jevokit.audit.log.dao.AuditLogDao;
import io.github.jevonsnotes.jevokit.audit.log.dao.BaseAutoDao;

import javax.persistence.Column;
import javax.persistence.Table;
import java.util.Date;

/**
 * 审计日志数据传输类
 * <p>PG库建表语句参照
 * <p>create table audit_log (
 *   "id" serial,
 *   "event_type" varchar(100)  NOT NULL,
 *   "user_code" varchar(50) ,
 *   "com_code" varchar(50) ,
 *   "source_ip" varchar(500),
 *   "local_ip" varchar(200),
 *   "port" int4 ,
 *   "scheme" varchar(20),
 *   "method" varchar(20),
 *   "content_type" varchar(200),
 *   "servlet_path" varchar(500) ,
 * 	"status" int2 ,
 * 	"audit_log_id" varchar(100),
 *   "start_time" timestamp,
 * 	"end_time" timestamp ,
 * 	"cost" int4,
 *   CONSTRAINT "audit_log_key" PRIMARY KEY ("id")
 * )
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder
@Table(name = AuditLogDao.TABLE_NAME)
public class AuditLogDto {

    /**
     * global_key
     */
    @Column(name = "global_key")
    private String globalKey;
    /**
     * parent_key
     */
    @Column(name = "parent_key")
    private String parentKey;
    /**
     * local_key
     */
    @Column(name = "local_key")
    private String localKey;
    /**
     * 事件类型
     */
    @Column(name = "event_type")
    private String eventType;
    /**
     * 事件名称
     */
    @Column(name = "event_name")
    private String eventName;
    /**
     * 用户代码
     */
    @Column(name = "user_code")
    private String userCode;
    /**
     * 机构代码
     */
    @Column(name = "com_code")
    private String comCode;
    /**
     * 源ip
     */
    @Column(name = "source_ip")
    private String sourceIp;
    /**
     * 目标ip
     */
    @Column(name = "local_ip")
    private String localIp;
    /**
     * 端口
     */
    @Column(name = "port")
    private Integer port;
    /**
     * 协议
     */
    @Column(name = "scheme")
    private String scheme;
    /**
     * 请求方法
     */
    @Column(name = "method")
    private String method;
    /**
     * 请求方法
     */
    @Column(name = "class_method")
    private String classMethod;
    /**
     * 文本类型
     */
    @Column(name = "content_type")
    private String contentType;
    /**
     * 请求路径
     */
    @Column(name = "servlet_path")
    private String servletPath;
    /**
     * 报名
     */
    @Column(name = "class_name")
    private String className;
    /**
     * 状态码
     */
    @Column(name = "status")
    private Integer status;

    /**
     * 开始时间
     */
    @Column(name = "start_time")
    private Date startTime;
    /**
     * 结束时间
     */
    @Column(name = "end_time")
    private Date endTime;
    /**
     * 耗时
     */
    @Column(name = "cost")
    private Long cost;


    @Column(name = "query_string")
    private String queryString;

    @Column(name = "request_body")
    private String requestBody;

    @Column(name = "response_body")
    private String responseBody;


}
