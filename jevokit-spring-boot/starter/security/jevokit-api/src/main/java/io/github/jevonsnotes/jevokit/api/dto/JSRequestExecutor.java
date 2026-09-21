package io.github.jevonsnotes.jevokit.api.dto;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.digest.MD5;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.TypeReference;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import io.github.jevonsnotes.jevokit.api.callback.IRequest;
import io.github.jevonsnotes.jevokit.api.config.JevokitApiClientProperties;
import io.github.jevonsnotes.jevokit.api.consts.ApiError;
import io.github.jevonsnotes.jevokit.api.consts.ApiHSignAlg;
import io.github.jevonsnotes.jevokit.api.exception.JSecureApiException;
import io.github.jevonsnotes.jevokit.api.util.JSecureApiUtil;
import io.github.jevonsnotes.jevokit.core.util.SecurityUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * JSecure 协议客户端执行器（门面式的流式构建器）：封装"作为调用方"发起一次安全接口请求的
 * 组装逻辑——请求头签名、数据 SM2 签名、SM4 加密与响应解密验签。
 * 使用方式：{@code JSRequestExecutor.create(clientId,url,data,R.class).transNo(...).execute((u,b,h)-> http 调用)}；
 * 传输载体由 {@link IRequest} 回调注入（不绑死 HTTP 客户端），execute 前未调用 prepare() 会自动补做。
 * <p>判定与返回语义：终端配置按 clientId 从 jevokit.api.client 列表匹配（缺失即抛错 fail-fast）；
 * execute 返回的 {@code JSecureResponse<R>} 已完成解密（view）与反序列化（target），
 * 终端开启 verify 时对响应验签，失败抛 {@link JSecureApiException}——不返回未验签的数据。
 * <p>关键约束：请求头签名与数据签名的密钥均来自终端配置（secretKey/私钥），调用方与服务端须成对登记。
 */

@Slf4j
public class JSRequestExecutor<T,R> {
    @Getter
    private final JSecureRequest<T> request = new JSecureRequest<>();
    @Getter
    private T data;
    @Getter
    private String clientId;
    @Getter
    private String url;
    @Getter
    private final Map<String, String> headers = new HashMap<>();

    @Getter
    private Class<R> responseClass;
    @Getter
    private boolean prepare;
    private JevokitApiClientProperties client;

    /** 无参构造（仅 create 工厂入口使用）。 */
    private JSRequestExecutor(){}

    /**
     * 创建一个执行器
     * @param clientId 终端识别码
     * @param url 地址
     * @param data 数据对象
     * @param responseClass 返回数据类型
     */
    private JSRequestExecutor(String clientId, String url, T data, Class<R> responseClass){
        this.url = url;
        this.responseClass = responseClass;
        this.data = data;
        this.clientId = clientId;
    }

    /**
     * 创建一个执行器
     * @param clientId 终端识别码
     * @param url 地址
     * @param data 数据对象
     * @param responseClass 返回数据类型
     * @return 执行器
     * @param <T> 数据类型
     * @param <R> 返回数据类型
     */
    public static <T,R> JSRequestExecutor<T,R> create(String clientId, String url, T data, Class<R> responseClass) {
        return new JSRequestExecutor<>(clientId,url,data,responseClass);
    }


    /**
     * 设置交易号（不设则报文内为空，由服务端自行判定唯一性约束）。
     *
     * @param transNo 交易号
     * @return 本执行器（链式）
     */
    public JSRequestExecutor<T,R> transNo(String transNo) {
        request.setTransNo(transNo);
        return this;
    }

    /**
     * 设置交易类型（服务端 @JSecureApi 声明了 transType 限定时必填，否则校验不过）。
     *
     * @param transType 交易类型
     * @return 本执行器（链式）
     */
    public JSRequestExecutor<T,R> transType(String transType) {
        request.setTransType(transType);
        return this;
    }


    /**
     * 组装协议要素：按 clientId 取终端配置，生成时间戳/nonce/请求头签名，
     * 数据 SM2 签名并按终端开关 SM4 加密。
     *
     * @return 本执行器（prepare 标记置真）
     * @throws JSecureApiException clientId 为空或终端未登记时抛出
     */
    public JSRequestExecutor<T,R> prepare(){
        Assert.notBlank(clientId,()-> new JSecureApiException(ApiError.FAIL.code(),"终端识别码不能为空"));
        client = JSecureApiUtil.getClient(clientId);
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String nonce = UUID.randomUUID().toString();
        // 生成请求头签名
        String signature = JSecureApiUtil.headerSignature(client.getHeader().getSignAlg(),timestamp,nonce,client.getSecretKey());

        // 封装请求头
        headers.put(JSecureApiUtil.rebuildHeader(client.getHeader().getClientIdName(), client.getHeader().getPrefix()), clientId);
        headers.put(JSecureApiUtil.rebuildHeader(client.getHeader().getTimestampName(), client.getHeader().getPrefix()), timestamp);
        headers.put(JSecureApiUtil.rebuildHeader(client.getHeader().getNonceName(), client.getHeader().getPrefix()), nonce);
        headers.put(JSecureApiUtil.rebuildHeader(client.getHeader().getSignatureName(), client.getHeader().getPrefix()), signature);

        // 封装请求数据对象
        String dataStr = JSON.toJSONString(this.data);

        // 生成数据签名
        request.setSignature(SecurityUtil.sm2Base64Sign(dataStr, client.getPrivateKey()));

        // 数据加密
        request.setData(client.isEncrypt() ? SecurityUtil.sm4cbcEncrypt(dataStr, client.getSecretKeySm4()) :dataStr );

        this.prepare = true;
        return this;
    }

    /**
     * 发起请求并处理响应：未 prepare 自动补做；经 {@link IRequest} 发送报文，
     * 响应解密（view）、反序列化（target）并按终端开关验签。
     *
     * @param callback 传输载体回调（任意 HTTP 客户端实现，headers 须原样透传）
     * @return 已解密验签的协议响应
     * @throws JSecureApiException 响应验签失败时抛出（不返回未验签数据）
     */
    public JSecureResponse<R> execute(IRequest callback) {

        if(!isPrepare()){
            prepare();
        }

        //发起请求
        String res = callback.call(getUrl(), JSON.toJSONString(getRequest()), getHeaders());
        //封装返回数据对象
        JSecureResponse<R> jsrRes = JSONObject.parseObject(res, new TypeReference<JSecureResponse<R>>() {
        });
        String resDataStr = jsrRes.getData();
        //数据体安全
        if (StrUtil.isNotBlank(resDataStr)) {
            //数据体解密
            if (client.isDecrypt()) {
                jsrRes.setView(SecurityUtil.sm4cbcDecrypt(resDataStr, client.getSecretKeySm4()));
            } else {
                jsrRes.setView(resDataStr);
            }
            jsrRes.setTarget(JSONObject.parseObject(jsrRes.getView(), getResponseClass()));
            //数据体验签
            if (client.isVerify()) {
                boolean verify = SecurityUtil.sm2Base64Verify(jsrRes.getView(), jsrRes.getSignature(), client.getPublicKey());
                if (!verify) {
                    log.error("返回结果验签失败: {}", res);
                    throw new JSecureApiException(ApiError.FAIL.code(), "返回结果验签失败");
                }
            }
        }
        return jsrRes;
    }
}
