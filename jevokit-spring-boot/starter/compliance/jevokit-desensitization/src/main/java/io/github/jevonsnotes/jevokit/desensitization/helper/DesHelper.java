package io.github.jevonsnotes.jevokit.desensitization.helper;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.text.StrBuilder;
import cn.hutool.core.util.ClassUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
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
public class DesHelper implements ApplicationListener<ContextRefreshedEvent> {
    private final JevokitDesensitizationProperties properties;
    private final SensitizationPowerInterceptor sensitizationPowerInterceptor;
    /** 就绪标志：init()（@PostConstruct）发布，ContextClosedEvent 清理（与发布配对，见 onContextClosed）。
     *  volatile——日志链路在 netty 等非容器线程上读取 */
    private static volatile boolean ready;
    /** init() 提升的静态配置引用。volatile——非容器线程直读（DesUtil 日志链路），
     *  且关闭清理后置空必须对读方可见 */
    public static volatile JevokitDesensitizationProperties staticProperties;
    /** init() 提升的静态权限拦截器引用。volatile 同上（checkPower 由任意线程调用） */
    public static volatile SensitizationPowerInterceptor staticSensitizationPowerInterceptor;
    /** 刷新发布提升的静态实例：日志链路专用实例源（见 getInstance 的死锁防御注释） */
    private static volatile DesHelper staticInstance;

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
        DesHelper helper = getInstance();
        if (helper == null) {
            // 实例未发布（init 后、ContextRefreshedEvent 前的刷新窗口）：跳过脱敏原样返回（fail-open），
            // 绝不退回 SpringUtil.getBean——刷新窗口期会死锁（见 getInstance 注释，DEF-1）
            return data;
        }
        return DesUtil.desByProcessTypeWrap(DstProcessType.VIEW, () -> helper.desensitizeData(data, deepClone));

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

    /**
     * 取启动时提升的静态实例（init 后可用，未 init 前为 null，调用方自行判空）。
     *
     * <p>死锁防御（DEF-1）：本方法在日志链路（logback 过滤器）上被任意线程调用，绝不允许走
     * SpringUtil.getBean——上下文刷新期间 main 线程持单例锁创建 bean（如 redisson），此刻任何
     * 线程的 getBean 都会阻塞在单例锁上；若被阻塞者恰是 main 所等 future 的完成方（Redisson
     * 连接回调打日志），即形成三方循环等待、JVM 永久挂起。故实例一律经 ContextRefreshedEvent
     * 静态提升获取，日志路径对 Spring 容器零依赖（未发布时由调用方 fail-open 跳过）。
     */
    private static DesHelper getInstance() {
        return staticInstance;
    }

    /** 取启动时提升的静态配置引用（init 前为 null，调用方自行判空）。 */
    private static JevokitDesensitizationProperties getProperties() {
        return staticProperties;
    }

    /** 容器就绪回调：把配置/权限拦截器提升为静态引用并置 ready 标志（静态入口的前置条件）。
     *  实例引用不在此发布——@PostConstruct 处 this 为裸实例且上下文仍在刷新（单例锁被持有），
     *  代理发布延后至 {@link #onApplicationEvent(ContextRefreshedEvent)}。 */
    @PostConstruct
    public void init() {
        staticProperties = this.properties;
        staticSensitizationPowerInterceptor = this.sensitizationPowerInterceptor;
        ready = true;
    }

    /** 上下文刷新完成回调：此刻单例锁已释放（finishRefresh 阶段），取容器实例发布到静态引用。
     *  经容器取而非记 this，保留 AOP 代理形态（@Desensitization 切面语义与原 getBean 一致）；
     *  刷新失败的上下文不触发本事件，日志链路按 fail-open 跳过（见 getInstance 死锁防御注释）。 */
    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        staticInstance = event.getApplicationContext().getBean(DesHelper.class);
    }

    /** 上下文关闭回调：清理 init()/刷新发布提升的全部静态引用（与发布配对的生命周期闭环）。
     *  <p>动机：spring-test 多上下文、热重启等场景下，旧上下文的静态引用若不清理，会在下一上下文
     *  刷新完成前被当作"就绪"实例继续使用（陈旧配置、已销毁实例——DEF-1 刷新窗口的放大器）。
     *  清理后各入口回到既有语义：desensitizeLog/desensitizeView fail-fast（not ready）、
     *  对象日志链路 fail-open 跳过、checkPower 安全默认 false（无权限），绝不进入容器查找。
     *  <p>写序约定：先清普通引用、最后落 volatile ready——读方一律以 ready 为门闩；
     *  多上下文语义为"最后刷新者胜出、任一关闭即清理"（静态字段为进程级单值，与发布侧一致）。
     *  单上下文生命周期内顺序不变：刷新发布 → 关闭清理，中间窗口静态引用始终可用。 */
    @EventListener(ContextClosedEvent.class)
    public void onContextClosed(ContextClosedEvent event) {
        staticInstance = null;
        staticProperties = null;
        staticSensitizationPowerInterceptor = null;
        ready = false;
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
                // 实例未发布（上下文刷新早期日志，如 redisson 连接初始化）：跳过脱敏原样输出——
                // 日志链路 fail-open（同 DesensitizedLogFilter"脱敏失败绝不阻断日志输出"语义），
                // 绝不允许退回 SpringUtil.getBean（上下文刷新期会死锁，见 getInstance 注释）
                DesHelper helper = getInstance();
                if (helper != null) {
                    obj = helper.desensitize(obj,false);
                }
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
                // 引用快照后再用：onContextClosed 清理可能并发置空静态引用，
                // 判空不满足时按安全默认（无权限）返回，绝不 NPE 阻断权限判定
                JevokitDesensitizationProperties snapshot = getProperties();
                SensitizationPowerInterceptor interceptor = staticSensitizationPowerInterceptor;
                if(snapshot != null && interceptor != null) {
                    List<String> keys = snapshot.getLevelPowerKey().get(level);
                    return interceptor.check(level,keys);
                }
                log.debug("DesHelper static refs cleared (context closed)");
            } else {
                log.debug("DesHelper not ready");
            }
        }
        return false;
    }
}
