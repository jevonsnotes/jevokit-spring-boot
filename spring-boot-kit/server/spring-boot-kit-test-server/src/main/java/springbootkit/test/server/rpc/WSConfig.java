package springbootkit.test.server.rpc;

import cn.hutool.extra.spring.SpringUtil;
import jakarta.xml.ws.Endpoint;
import lombok.extern.slf4j.Slf4j;
import org.apache.cxf.Bus;
import org.apache.cxf.bus.spring.SpringBus;
import org.apache.cxf.jaxws.EndpointImpl;
import org.apache.cxf.transport.servlet.CXFServlet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import springbootkit.test.server.rpc.producer.ProducerService;



/**
 * @Description TODO
 * @Date 2024/6/11 10:32
 * @Version 0.0.1
 */
@Slf4j
@Configuration
public class WSConfig {

    private String endpointAddr = "/producerService";
    private String servletContext = "/webService";
    private String port = SpringUtil.getBean(Environment.class).getProperty("server.port");
    @Autowired
    private ProducerService producerService;

    @Bean
    public ServletRegistrationBean disServlet(){
        return new ServletRegistrationBean(new CXFServlet(),servletContext +"/*"); //webservice下的请求将有CXFServlet处理
    }

    @Bean(name = Bus.DEFAULT_BUS_ID)
    public SpringBus springBus(){
        return  new SpringBus();
    }

    @Bean
    public Endpoint endpoint(){

        EndpointImpl endpoint = new EndpointImpl(springBus(), producerService);
        endpoint.publish(endpointAddr);
        System.out.println("服务发布成功！地址为：http://localhost:"+port+servletContext+endpointAddr+"?wsdl");
        return endpoint;
    }

}



