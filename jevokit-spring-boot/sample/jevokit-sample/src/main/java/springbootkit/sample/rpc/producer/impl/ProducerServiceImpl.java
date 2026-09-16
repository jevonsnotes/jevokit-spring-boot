package springbootkit.sample.rpc.producer.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.support.SpringBeanAutowiringSupport;
import springbootkit.sample.rpc.producer.ProducerService;

import jakarta.jws.WebMethod;
import jakarta.jws.WebService;


/**
 * @Description TODO
 * @Date 2024/6/11 10:26
 * @Version 0.0.1
 */

@Slf4j
@Service
@WebService(serviceName="ProducerService",
        endpointInterface = "springbootkit.sample.rpc.producer.ProducerService",
        targetNamespace = "http://producerService.producer.rpc.server.test.spring.springbootkit")
public class ProducerServiceImpl implements ProducerService {

    @Override
    public Account getAccount(String name) {
        log.info("{} 获取账户",name);
        Account account = new Account();
        account.setName(name + "的账号");
        return account;
    }
}
