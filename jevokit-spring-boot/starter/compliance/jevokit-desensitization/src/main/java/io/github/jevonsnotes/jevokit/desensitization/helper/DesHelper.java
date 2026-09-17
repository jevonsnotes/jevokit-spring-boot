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
     * 生成脱敏视图（深克隆默认开启，原始对象不被修改）。
     *
     * @param data 待脱敏对象（按 @DstProperty 标注与属性名规则处理）
     * @param <T>  对象类型
     * @return 克隆并脱敏后的新对象
     */
    public static <T> T desensitizeView(T data) {
        return desensitizeView(data, true);
    }

    /**
     * 生成脱敏视图（算法取 algorithm.view 链路配置）。
     *
     * @param data      待脱敏对象
     * @param deepClone true 深克隆后脱敏（原对象不动）；false 原地脱敏（调用方自担副作用）
     * @param <T>       对象类型
     * @return 脱敏后的对象
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
    /**
     * 原地/克隆脱敏（实例方法，日志链路递归处理对象时复用）。
     *
     * @param data      待脱敏对象（须配合 @Desensitization 触发切面语义）
     * @param deepClone true 先深克隆
     * @param <T>       对象类型
     * @return 脱敏后的对象
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


    /**
     * 数据脱敏实体方法：按需深克隆后原值返回——真正遍历由 @Desensitization 切面对本方法的环绕完成
     * （切面经 SecurityUtil.filterStr 逐字段清洗，见 DesensitizationAspect）。
     */
    private <T> T desensitizeData(T data, boolean deepClone) {
        if (deepClone) {
            data = CloneUtil.deep(data);
        }
        return data;
    }

    /** 视图/日志链路切换入口（委托 {@link DesUtil}，语义见其同名方法注释）。 */
    public static <T>T desByProcessTypeWrap(DstProcessType processType, CallbackR<T> callback) {

        return DesUtil.desByProcessTypeWrap(processType,callback);
    }

    /** 视图/日志链路切换入口（无返回值形态，语义同上）。 */
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
    /**
     * 深度克隆并脱敏（等价 desensitize(data, true)，原始对象不被修改）。
     *
     * @param data 待脱敏对象
     * @param <T>  对象类型
     * @return 克隆并脱敏后的新对象
     */
    @Desensitization
    public <T> T desensitize(T data) {
        return desensitizeData(data, true);
    }

    /** 取容器中的 DesHelper 单例（静态入口转发用）。 */
    private static DesHelper getInstance() {
        return SpringUtil.getBean(DesHelper.class);
    }

    /** 取启动时提升的静态配置引用（init 前为 null，调用方自行判空）。 */
    private static JevokitDesensitizationProperties getProperties() {
        return staticProperties;
    }

    /** 容器就绪回调：把配置/权限拦截器提升为静态引用并置 ready 标志（静态入口的前置条件）。 */
    @PostConstruct
    public void init() {
        staticProperties = this.properties;
        staticSensitizationPowerInterceptor = this.sensitizationPowerInterceptor;
        ready = true;
    }




    /**
     * 脱敏日志对象（深克隆后递归处理：JSON/XML 字符串按报文脱敏，Map/List 逐项，
     * 普通对象按标注脱敏；算法取 algorithm.log 链路）。
     *
     * @param obj 日志参数对象；null 原样返回
     * @return 克隆并脱敏后的新对象（入参不被修改，日志可安全输出）
     */
    public static Object desensitizeLog(Object obj) {
        Assert.isTrue(ready, "desHelper not ready");
        return DesUtil.desByProcessTypeWrap(DstProcessType.LOG, () -> desensitizeLogAfterClone(CloneUtil.deep(obj)));
    }

    /**
     * 克隆后递归脱敏日志对象：String 按 JSON/XML 报文规则（非报文原样）；Map 按键名规则处理
     * 字符串值、复杂值递归；List 逐元素递归；普通对象走标注脱敏（desensitize，不二次克隆）。
     */
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

    /**
     * 权限判定：查该级别映射的权限代码集，交 SensitizationPowerInterceptor 判定当前用户可否查看明文。
     *
     * @param level 敏感级别；null 或 helper 未就绪时返回 false（安全默认：无权限）
     * @return true 允许查看明文
     */
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
