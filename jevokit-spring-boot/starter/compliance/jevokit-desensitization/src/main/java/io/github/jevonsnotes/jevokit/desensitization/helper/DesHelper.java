package io.github.jevonsnotes.jevokit.desensitization.helper;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.text.StrBuilder;
import cn.hutool.core.util.ClassUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.github.jevonsnotes.jevokit.core.lang.Callback;
import io.github.jevonsnotes.jevokit.core.lang.CallbackR;
import io.github.jevonsnotes.jevokit.core.util.CloneUtil;
import io.github.jevonsnotes.jevokit.core.util.JsonUtil;
import io.github.jevonsnotes.jevokit.core.util.XmlUtil;
import io.github.jevonsnotes.jevokit.desensitization.annotation.Desensitization;
import io.github.jevonsnotes.jevokit.desensitization.config.JevokitDesensitizationProperties;
import io.github.jevonsnotes.jevokit.desensitization.consts.DstLevel;
import io.github.jevonsnotes.jevokit.desensitization.consts.DstProcessType;
import io.github.jevonsnotes.jevokit.desensitization.interceptor.SensitizationPowerInterceptor;
import io.github.jevonsnotes.jevokit.desensitization.util.DesUtil;

//import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 脱敏辅助类
 */
@Slf4j
@RequiredArgsConstructor
public class DesHelper {
    private final JevokitDesensitizationProperties properties;
    private final SensitizationPowerInterceptor sensitizationPowerInterceptor;
    private static boolean ready;
    public static JevokitDesensitizationProperties staticProperties;
    public static SensitizationPowerInterceptor staticSensitizationPowerInterceptor;

//    /**
//     * 脱敏日志。日志默认不做权限校验，符合标记即脱敏
//     *
//     * @param data
//     * @param <T>
//     * @return
//     */
//    public static <T> T desensitizeLogByAlgWrap(T data,boolean deepClone) {
//        Assert.isTrue(ready, "JevokitDesensitizationProperties not ready");
//        try {
//            DesUtil.setLocalAlgorithm(getProperties().getAlgorithm().getLog());
//            return getInstance().desensitize(data,deepClone);
//        } finally {
//            DesUtil.removeLocalAlgorithm();
//        }
//    }

//    /**
//     * 脱敏日志。日志默认不做权限校验，符合标记即脱敏
//     */
//    public static String desensitizeLogByProp(String propName, String value) {
//        Assert.isTrue(ready, "JevokitDesensitizationProperties not ready");
//
//        try {
//            DesUtil.setLocalAlgorithm(getProperties().getAlgorithm().getLog());
//            return DesUtil.desByProperties(propName, value);
//        } finally {
//            DesUtil.removeLocalAlgorithm();
//        }
//
//    }

    /**
     * 脱敏视图
     * @param data
     * @param <T>
     * @return 返回克隆对象的脱敏视图
     */
    public static <T> T desensitizeView(T data) {
        return desensitizeView(data, true);
    }

    /**
     * 脱敏视图
     *
     * @param data
     * @param <T>
     * @return
     */
    public static <T> T desensitizeView(T data, boolean deepClone) {
        Assert.isTrue(ready, "desHelper not ready");
        return DesUtil.desByProcessTypeWrap(DstProcessType.VIEW, () -> getInstance().desensitizeData(data, deepClone));

    }


    /**
     * 脱敏，属性必须标注 @Desensitization
     *
     * @param data
     * @param deepClone
     * @param <T>
     * @return
     */
    @Desensitization
    public <T> T desensitize(T data, boolean deepClone) {
        return desensitizeData(data, deepClone);
//            //ObjectUtil.cloneByStream依赖Serializable接口，建议将数据载体实现Serializable接口，否则将触发json实现深度复制
//            T temp = null;
//            try {
//                temp = ObjectUtil.cloneByStream(data);
//            } catch (Exception e) {
//                // nothing output
//                log.debug("流式克隆失败，将使用json方式深克隆",e);
//            }
//            if(temp == null){
//                String jsonString = JSONObject.toJSONString(data, SerializerFeature.WriteClassName);
//                temp = JSONObject.parseObject(jsonString, ClassUtil.getClass(data), Feature.SupportAutoType);
//            }
//            data = temp;
//        }
//        return data;
    }


    private <T> T desensitizeData(T data, boolean deepClone) {
        if (deepClone) {
            data = CloneUtil.deep(data);
        }
        return data;
    }

    public static <T>T desByProcessTypeWrap(DstProcessType processType, CallbackR<T> callback) {

        return DesUtil.desByProcessTypeWrap(processType,callback);
    }

    public static void desByProcessTypeWrap(DstProcessType processType, Callback callback) {
        DesUtil.desByProcessTypeWrap(processType,callback);
    }


    /**
     * 深度克隆并脱敏，属性必须标注 @Desensitization
     *
     * @param data
     * @param <T>
     * @return 返回克隆对象的脱敏视图
     */
    @Desensitization
    public <T> T desensitize(T data) {
        return desensitizeData(data, true);
    }

    private static DesHelper getInstance() {
        return SpringUtil.getBean(DesHelper.class);
    }

    private static JevokitDesensitizationProperties getProperties() {
        return staticProperties;
    }

    @PostConstruct
    public void init() {
        staticProperties = this.properties;
        staticSensitizationPowerInterceptor = this.sensitizationPowerInterceptor;
        ready = true;
    }




    /**
     * 脱敏日志对象
     * @param obj
     * @return 返回克隆对象的脱敏对象
     */
    public static Object desensitizeLog(Object obj) {
        Assert.isTrue(ready, "desHelper not ready");
        return DesUtil.desByProcessTypeWrap(DstProcessType.LOG, () -> desensitizeLogAfterClone(CloneUtil.deep(obj)));
    }

    private static Object desensitizeLogAfterClone(Object obj){
        if (obj != null) {
            if (obj instanceof String) {
                if (JSONUtil.isTypeJSONObject((String) obj)) {
                    obj = DesUtil.desensitizeJson((String) obj);
//                    JSONObject jsonObject = JSONObject.parseObject((String) obj);
//                    JsonUtil.editJsonObj(jsonObject, (key, val) -> {
//                        if (val != null) {
//                            return DesUtil.desByProperties(key, val.toString());
//                        }
//                        return null;
//                    });
//                    obj = JSON.toJSONString(jsonObject);
                } else if (XmlUtil.isXml((String) obj)) {
                    obj = DesUtil.desensitizeXml((String) obj);
                }
            } else if (obj instanceof Map) {
                final Map<Object,Object> finalObj = (Map)obj;
                finalObj.forEach((k, v) ->{
                    if(v != null){
                        if(v instanceof String && !JSONUtil.isTypeJSONObject((String) v) && !XmlUtil.isXml((String) v)) {
                            if(k instanceof String){
                                finalObj.put(k,DesUtil.desByProperties((String)k, (String)v));
                            }
                        } else {
                            finalObj.put(k, desensitizeLogAfterClone(v));
                        }
                    }
                });
            } else if (obj instanceof List) {

                final List<Object> finalObj = (List<Object>)obj;

                for(int i =0;i<finalObj.size();i++){
                    if(finalObj.get(i) != null){
                        finalObj.set(i, desensitizeLogAfterClone(finalObj.get(i)));
                    }
                }

            } else if (ClassUtil.isNormalClass(obj.getClass()) && !ClassUtil.isSimpleValueType(obj.getClass())) {
                obj = getInstance().desensitize(obj,false);
            }
        }
        return obj;
    }

    public static boolean checkPower(DstLevel level){
        if(level != null) {
            if(ready){
                List<String> keys = getProperties().getLevelPowerKey().get(level);
                return staticSensitizationPowerInterceptor.check(level,keys);
            } else {
                log.debug("DesHelper not ready");
            }
        }
        return false;
    }
}
