package springbootkit.test.server;

import cn.hutool.core.io.IoUtil;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import springbootkit.desensitization.util.DesUtil;
import springbootkit.redis.token.strategy.DefaultTokenStrategy;

import java.io.FileInputStream;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {SpringBootKitTestServerApplication.class})
public class ApplicationTest {
    @Autowired
    private RedissonClient client;
    @Autowired
    private DefaultTokenStrategy defaultTokenStrategy;


    @Test
    public void contextLoads() {
        defaultTokenStrategy.getTokenDelegate();
        String token = defaultTokenStrategy.getTokenDelegate();
        System.out.println(token);
        String newToken = defaultTokenStrategy.refreshTokenDelegate();
        System.out.println(newToken);
    }
    @Test
    public void testMain(){
        testCost();
    }
    public static void testCost(){
        try (FileInputStream fis = new FileInputStream("D:\\Desktop\\报文.txt")){
            String xml = IoUtil.read(fis, "utf-8");
            String s = DesUtil.desensitizeXml(xml);
            System.out.println(s);

        } catch (Exception e){
            e.printStackTrace();
        }
    }

}
