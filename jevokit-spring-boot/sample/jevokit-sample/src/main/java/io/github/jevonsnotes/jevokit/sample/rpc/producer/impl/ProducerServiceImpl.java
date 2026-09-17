package io.github.jevonsnotes.jevokit.sample.rpc.producer.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.support.SpringBeanAutowiringSupport;
import io.github.jevonsnotes.jevokit.sample.rpc.producer.ProducerService;

import jakarta.jws.WebMethod;
import jakarta.jws.WebService;


 /**
 * SOAP 服务实现：@WebService 的 endpointInterface 指向 SEI 全限定名（包名迁移
 * 时此字符串必须同步，见 rename 教训），targetNamespace 按新包反演命名。
 */

@Slf4j
@Service
@WebService(serviceName="ProducerService",
        endpointInterface = "io.github.jevonsnotes.jevokit.sample.rpc.producer.ProducerService",
        targetNamespace = "http://jevonsnotes.github.io/jevokit/sample/rpc/producer")
public class ProducerServiceImpl implements ProducerService {

    @Override
    /**
    * SOAP 服务实现：按名构造账户回显（演示用途）。
    * @param name 姓名
    * @return 账户对象
    */
    public Account getAccount(String name) {
        log.info("{} 获取账户",name);
        Account account = new Account();
        account.setName(name + "的账号");
        return account;
    }
}
