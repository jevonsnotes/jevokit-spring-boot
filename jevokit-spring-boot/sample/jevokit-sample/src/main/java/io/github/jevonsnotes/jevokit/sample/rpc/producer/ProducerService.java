package io.github.jevonsnotes.jevokit.sample.rpc.producer;

import jakarta.jws.WebService;
import lombok.Data;

import jakarta.jws.WebParam;
import jakarta.jws.WebResult;
import java.io.Serializable;

/**
 * @Description TODO
 * @Date 2024/6/11 10:16
 * @Version 0.0.1
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