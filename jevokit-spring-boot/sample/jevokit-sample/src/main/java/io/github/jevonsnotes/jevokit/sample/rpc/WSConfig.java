package io.github.jevonsnotes.jevokit.sample.rpc;

import cn.hutool.extra.spring.SpringUtil;
import javax.xml.ws.Endpoint;
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
import io.github.jevonsnotes.jevokit.sample.rpc.producer.ProducerService;



 /**
 * CXF WebService 端点发布配置：将 ProducerService 以 /webService/producerService
 * 路径发布为 SOAP 端点（endpointAddr/port 运行时取值），演示 kit 与 CXF 共存。
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
    /**
    * CXF Servlet 注册：/webService/* 交由 CXF 处理。
    * @return Servlet 注册 Bean
    */
    public ServletRegistrationBean disServlet(){
        return new ServletRegistrationBean(new CXFServlet(),servletContext +"/*"); //webservice下的请求将有CXFServlet处理
    }

    @Bean(name = Bus.DEFAULT_BUS_ID)
    /**
    * CXF Spring 总线 Bean。
    * @return SpringBus
    */
    public SpringBus springBus(){
        return  new SpringBus();
    }

    @Bean
    /**
    * 发布 SOAP 端点：ProducerService 挂到 /webService/producerService（发布地址启动时打印）。
    * @return 已发布端点
    */
    public Endpoint endpoint(){

        EndpointImpl endpoint = new EndpointImpl(springBus(), producerService);
        endpoint.publish(endpointAddr);
        System.out.println("服务发布成功！地址为：http://localhost:"+port+servletContext+endpointAddr+"?wsdl");
        return endpoint;
    }

}



