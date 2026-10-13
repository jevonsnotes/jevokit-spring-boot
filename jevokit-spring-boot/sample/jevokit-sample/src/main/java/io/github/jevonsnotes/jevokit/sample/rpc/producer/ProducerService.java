package io.github.jevonsnotes.jevokit.sample.rpc.producer;

import javax.jws.WebService;
import lombok.Data;

import javax.jws.WebParam;
import javax.jws.WebResult;
import java.io.Serializable;

 /**
 * SOAP 服务契约（SEI）：getAccount 演示 @WebParam/@WebResult 命名映射，
 * 内嵌 Account DTO；由 ProducerServiceImpl 实现并经 WSConfig 发布。
 */
@WebService
public interface ProducerService {

    @WebResult(name = "resultName")
    Account getAccount(@WebParam(name = "name") String name);


    @Data
    class Account implements Serializable {
        private String name;
    }
}