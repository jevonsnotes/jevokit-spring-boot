package springbootkit.api.dto;

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
import springbootkit.api.callback.IRequest;
import springbootkit.api.config.SpringBootKitApiClientProperties;
import springbootkit.api.consts.ApiError;
import springbootkit.api.consts.ApiHSignAlg;
import springbootkit.api.exception.JSecureApiException;
import springbootkit.api.util.JSecureApiUtil;
import springbootkit.core.util.SecurityUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * @Description TODO
 * @Date 2025/4/15
 * @Version 0.0.1
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
    private SpringBootKitApiClientProperties client;

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


    public JSRequestExecutor<T,R> transNo(String transNo) {
        request.setTransNo(transNo);
        return this;
    }

    public JSRequestExecutor<T,R> transType(String transType) {
        request.setTransType(transType);
        return this;
    }


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
