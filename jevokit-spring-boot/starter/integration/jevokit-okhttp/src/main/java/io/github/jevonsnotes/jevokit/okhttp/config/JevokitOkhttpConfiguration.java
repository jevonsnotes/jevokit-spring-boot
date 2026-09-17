package io.github.jevonsnotes.jevokit.okhttp.config;

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
import io.github.jevonsnotes.jevokit.core.util.CollectionUtil;
import io.github.jevonsnotes.jevokit.okhttp.interceptor.CustomOkhttpConfiguration;
import io.github.jevonsnotes.jevokit.okhttp.util.OkHttpUtil;

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
 * okhttp 自动装配：构建直连/代理双客户端 + 连接池 + OkHttpUtil 门面 Bean。
 *
 * <p>用法：按 spkit.ok.http.* 配置装配；个性化定制注册 CustomOkhttpConfiguration Bean
 * （缺省提供空实现占位， @ConditionalOnMissingBean 兜底）。
 *
 * <p>关键约束与取舍：
 * <ul>
 *   <li>两客户端共享同一 createBasicBuilder 基座（超时/连接池/SSL/禁重试），代理客户端额外挂
 *       ProxySelector——always 模式由此实现"代理不可用自动切换下一个"（connectFailed 时从缓存剔除）；</li>
 *   <li>代理缓存 PROXY_CACHE / PROXY_CACHE_URI 为 static 可变集合：进程级共享、uri 级隔离
 *       （CheckLevel），剔除后无恢复逻辑，重启才重建——多实例部署时各进程独立熔断；</li>
 *   <li>信任所有证书（X509TrustManager 空实现 + hostnameVerifier 恒真）：为兼容自签内网代理的
 *       有意取舍，等于放弃服务端身份校验，仅宜在受控网络使用。</li>
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
@Configuration
//@EnableConfigurationProperties(OkhttpProperties.class)
public class JevokitOkhttpConfiguration {
    public static final Map<URI,List<Proxy>> PROXY_CACHE_URI = new HashMap<>();
    public static final List<Proxy> PROXY_CACHE = new ArrayList<>();
    @Lazy
    @Autowired
    private OkhttpProperties properties;

    @Bean
/**
    * 门面 Bean：直连 + 代理双客户端一次装配。
    * @param properties 行为配置
    * @param customOkhttpConfiguration 使用方个性化定制（缺省空实现占位）
    * @param pool 共享连接池
    * @return OkHttpUtil 门面单例
    */
    public OkHttpUtil okhttpUtil(OkhttpProperties properties, CustomOkhttpConfiguration customOkhttpConfiguration,ConnectionPool pool){
        return  new OkHttpUtil(properties,okHttpClient(customOkhttpConfiguration,pool),okHttpProxyClient(customOkhttpConfiguration,pool));
    }
    @RefreshScope
    @Bean
/**
    * 配置 Bean（@RefreshScope 支持热更新）。
    * @return spkit.ok.http.* 绑定实例
    */
    public OkhttpProperties okhttpProperties(){
        return new OkhttpProperties();
    }

    @ConditionalOnMissingBean
    @Bean
/**
    * 个性化定制缺省占位：空实现，@ConditionalOnMissingBean 兜底——使用方注册自己的
    * CustomOkhttpConfiguration Bean 即覆盖。
    * @return 空实现实例
    */
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
/**
    * 直连客户端 Bean（@Primary）：基础 Builder + 个性化定制，不带代理。
    * @param customOkhttpConfiguration 个性化定制回调
    * @param pool 共享连接池
    * @return 直连 OkHttpClient
    */
    public OkHttpClient okHttpClient(CustomOkhttpConfiguration customOkhttpConfiguration,ConnectionPool pool) {

        // 获取基础配置
        OkHttpClient.Builder builder = createBasicBuilder(pool);
        //个性化配置，通过实现CustomOkhttpConfiguration类并注入bean来实现
        customOkhttpConfiguration.configClient(builder);

        return builder.build();
    }
    @Bean(name = "OKHTTP_PROXY_CLIENT")
/**
    * 代理客户端 Bean：基础 Builder + initProxy（ProxySelector 多代理切换 + 可选代理认证）
    * + 个性化定制。
    * @param customOkhttpConfiguration 个性化定制回调（作用于代理客户端）
    * @param pool 共享连接池
    * @return 代理 OkHttpClient（Bean 名 OKHTTP_PROXY_CLIENT）
    */
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
/**
    * 信任所有证书的 TrustManager（兼容自签内网代理的有意取舍，见类注释）。
    * @return 空校验实现
    */
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
/**
    * 随信任策略构建的 SSLSocketFactory；构建失败记日志并返回 null（OkHttp 回落默认工厂）。
    * @return TLS SSLSocketFactory，失败时 null
    */
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
/**
    * 两客户端共享的连接池：容量与保活时长取自配置（maxIdleConnections/keepAliveDuration）。
    * @param properties 连接池参数来源
    * @return 共享连接池
    */
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
