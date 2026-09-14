package springbootkit.test.server;

import cn.hutool.core.io.IoUtil;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import springbootkit.okhttp.interceptor.CustomOkhttpConfiguration;
import springbootkit.okhttp.interceptor.CustomOkhttpProxyConfiguration;
import springbootkit.okhttp.util.OkHttpUtil;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest(classes = {SpringBootKitTestServerApplication.class})
public class OkhttpTest {

    @Test
    public void mainTest() {
        testOkhttpProxy();
//        testGbkServer();
//        testDownload();
    }
    @Test
    public void testOkhttpProxy() {
        String res = null;
//        try {
//            OkHttpUtil okHttpUtil = OkHttpUtil.of(new CustomOkhttpConfiguration() {
//                @Override
//                public void configClient(OkHttpClient.Builder builder) {
//                    builder.readTimeout(3600, TimeUnit.SECONDS);
//                }
//
//                @Override
//                public void configProxyClient(OkHttpClient.Builder builder) {
//                    builder.readTimeout(3600, TimeUnit.SECONDS);
//                }
//            });
//            res = okHttpUtil.get("https://www.baidu.com");
//        } catch (Exception e) {
//            log.error(e.getMessage(), e);
//        }
        try {
            res = OkHttpUtil.ofProxy().get("https://www.baidu.com/abc");
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
//        try {
//            res = OkHttpUtil.of().get("https://www.baidu.com");
//        } catch (Exception e) {
//            log.error(e.getMessage(), e);
//        }
        log.info(res);
    }

    public void testGbkServer(){
        String xml = "";
        String url = "http://localhost:8032/mock/ApplyXMLServlet";
        String s = OkHttpUtil.of().post(url, Charset.forName("gbk"));
        System.out.println(s);
    }
    public void testDownload(){
        String url = "https://ggzy.guizhou.gov.cn/hallweb/hall/attach/nosession/download?attachId=8a8bb7d78605cd6901864f051bef2cca";
        byte[] bytes = OkHttpUtil.of().download(url);
        try (FileOutputStream fos = new FileOutputStream("H:\\temp\\xxx.pdf")){
            IoUtil.write(fos,true,bytes);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
