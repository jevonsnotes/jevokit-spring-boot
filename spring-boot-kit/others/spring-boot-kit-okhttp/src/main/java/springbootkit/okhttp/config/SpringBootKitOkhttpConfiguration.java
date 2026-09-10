package springbootkit.okhttp.config;

import cn.hutool.core.text.AntPathMatcher;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Authenticator;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import springbootkit.core.util.CollectionUtil;
import springbootkit.okhttp.interceptor.CustomOkhttpConfiguration;
import springbootkit.okhttp.util.OkHttpUtil;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.*;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @Description TODO
 * @Date 2024/9/2
 * @Version 0.0.1
 */
@Slf4j
@RequiredArgsConstructor
@Configuration
//@EnableConfigurationProperties(OkhttpProperties.class)
public class SpringBootKitOkhttpConfiguration {
    public static final Map<URI,List<Proxy>> PROXY_CACHE_URI = new HashMap<>();
    public static final List<Proxy> PROXY_CACHE = new ArrayList<>();
    @Lazy
    @Autowired
    private OkhttpProperties properties;

    @Bean
    public OkHttpUtil okhttpUtil(OkhttpProperties properties, CustomOkhttpConfiguration customOkhttpConfiguration,ConnectionPool pool){
        return  new OkHttpUtil(properties,okHttpClient(customOkhttpConfiguration,pool),okHttpProxyClient(customOkhttpConfiguration,pool));
    }
    @RefreshScope
    @Bean
    public OkhttpProperties okhttpProperties(){
        return new OkhttpProperties();
    }

    @ConditionalOnMissingBean
    @Bean
    public CustomOkhttpConfiguration customOkhttpInterceptor(){
        return new CustomOkhttpConfiguration() {
            @Override
            public void configClient(OkHttpClient.Builder builder) {

            }

            @Override
            public void configProxyClient(OkHttpClient.Builder builder) {

            }
        };
    }
    @Primary
    @Bean
    public OkHttpClient okHttpClient(CustomOkhttpConfiguration customOkhttpConfiguration,ConnectionPool pool) {

        // 获取基础配置
        OkHttpClient.Builder builder = createBasicBuilder(pool);
        //个性化配置，通过实现CustomOkhttpConfiguration类并注入bean来实现
        customOkhttpConfiguration.configClient(builder);

        return builder.build();
    }
    @Bean(name = "OKHTTP_PROXY_CLIENT")
    public OkHttpClient okHttpProxyClient(CustomOkhttpConfiguration customOkhttpConfiguration,ConnectionPool pool) {
        // 获取基础配置
        OkHttpClient.Builder builder = createBasicBuilder(pool);
        // 增加代理配置
        initProxy(builder);
        //个性化配置，通过实现CustomOkhttpConfiguration类并注入bean来实现
        customOkhttpConfiguration.configProxyClient(builder);
        return builder.build();
    }

    /**
     * 创建一个通用builder
     * @return
     */
    public OkHttpClient.Builder createBasicBuilder(ConnectionPool pool){
        return new OkHttpClient.Builder()
                .sslSocketFactory(sslSocketFactory(), x509TrustManager())
                // 是否开启缓存
                .retryOnConnectionFailure(false)
                .connectionPool(pool)
                .connectTimeout(properties.getConnectTimeout(), TimeUnit.SECONDS)
                .readTimeout(properties.getReadTimeout(), TimeUnit.SECONDS)
                .writeTimeout(properties.getWriteTimeout(), TimeUnit.SECONDS)
                .hostnameVerifier((hostname, session) -> true);
    }

    /**
     * 初始化代理配置
     * @param builder
     */
    public void initProxy(OkHttpClient.Builder builder ){
        builder
                .hostnameVerifier((hostname, session) -> true)
                .eventListener(new EventListener() {
                    @Override
                    public void connectFailed(@NotNull Call call, @NotNull InetSocketAddress inetSocketAddress, @NotNull Proxy proxy, @Nullable Protocol protocol, @NotNull IOException ioe) {
                        if(properties.getProxyCheckLevel().equals(OkhttpProperties.CheckLevel.all)){
                            log.error("connect failed, remove the proxy [{}] from the proxy-cache", proxy);
                            PROXY_CACHE.remove(proxy);
                        } else {
                            URI uri = call.request().url().uri();
                            List<Proxy> proxies = PROXY_CACHE_URI.get(uri);
                            if(CollectionUtil.isNotEmpty(proxies)){

                                log.error("connect failed, remove the proxy [{}] from the proxy-cache for uri [{}]", proxy, uri);
                                proxies.remove(proxy);

                            }
                        }
                    }
                })
                // 设置代理
                .proxySelector(new ProxySelector() {
                    @Override
                    public List<Proxy> select(URI uri) {
                        boolean proxyIgnored = isProxyIgnored(uri.getHost());
                        List<Proxy> proxies;
                        if(proxyIgnored){
                            proxies = CollectionUtil.toList(Proxy.NO_PROXY);
                        } else {
                            proxies = getProxyFromCache(uri);
                        }
                        return CollectionUtil.isEmpty(proxies) ? resetProxies(uri) : proxies;
                    }

                    @Override
                    public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
                        log.error("connect failed, url [{}] , socket [{}]", uri,sa);
                    }
                })
        ;
        //设置代理服务器账号密码
        if(StrUtil.isNotBlank(properties.getProxyUsername())){
            Authenticator authenticator = (route, response) -> {

                String credential = Credentials.basic(properties.getProxyUsername(), properties.getProxyPassword());
                return response.request().newBuilder()
                        .header("Proxy-Authorization", credential)
                        .build();
            };
            builder.proxyAuthenticator(authenticator);
        }
    }
    @Bean
    public X509TrustManager x509TrustManager() {
        return new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType){
            }
            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType){
            }
            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        };
    }
    @Bean
    public SSLSocketFactory sslSocketFactory() {
        try {
            // 信任任何链接
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{x509TrustManager()}, new SecureRandom());
            return sslContext.getSocketFactory();
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            log.error(e.getMessage(),e);
        }
        return null;
    }
    @Bean
    public ConnectionPool pool(OkhttpProperties properties) {
        return new ConnectionPool(properties.getMaxIdleConnections(), properties.getKeepAliveDuration(), TimeUnit.SECONDS);
    }



    private boolean isProxyIgnored(String host) {
        boolean isIgnore = false;
        AntPathMatcher antPathMatcher = new AntPathMatcher();
        // 代理绕过
        for (String ignoreHost : this.properties.getProxyIgnores()) {
            if (antPathMatcher.match(ignoreHost, host)) {
                isIgnore = true;
                log.debug("ignore used proxy host[{}][{}][{}]", host, ignoreHost, isIgnore);
                break;
            }
        }
        return isIgnore;
    }
    private List<Proxy> getProxyFromCache(URI uri) {
        if(properties.getProxyCheckLevel().equals(OkhttpProperties.CheckLevel.all)){
            if(CollectionUtil.isNotEmpty(PROXY_CACHE)){
                return PROXY_CACHE;
            }
        } else {
            if(CollectionUtil.isNotEmpty(PROXY_CACHE_URI.get(uri))){
                return PROXY_CACHE_URI.get(uri);
            }
        }
        return null;
    }

    private List<Proxy> resetProxies(URI uri){
        List<Proxy> proxies = new ArrayList<>();
        properties.getProxyHosts().forEach(proxy ->{
            String[] hostAndPort = StrUtil.trimToEmpty(proxy).split(":");
            if(hostAndPort.length == 2){
                proxies.add(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(hostAndPort[0], Integer.parseInt(hostAndPort[1]))));
            }
        });
        if(properties.getProxyCheckLevel().equals(OkhttpProperties.CheckLevel.all)){
            PROXY_CACHE.addAll(proxies);
        } else {
            PROXY_CACHE_URI.put(uri,proxies);
        }
        return proxies;
    }
}
