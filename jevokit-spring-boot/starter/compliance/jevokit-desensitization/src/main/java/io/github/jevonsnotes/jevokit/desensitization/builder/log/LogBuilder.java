package io.github.jevonsnotes.jevokit.desensitization.builder.log;

/**
 * 结构化日志构建器：以"标题 + 键值元数据"组织日志内容，build() 时对全部元数据值
 * 走日志链路脱敏（DesHelper.desensitizeLog，算法取 jevokit.desensitization.algorithm.log）。
 * 使用方式：{@code LogBuilder.create().title("下单").meta("手机号",mobile).metaJson("订单",order).build()}
 * ——脱敏只发生在 build()，构建阶段可安全持有明文；metaJson 自动序列化对象后参与脱敏。
 * <p>关键约束：build() 只脱敏一次（重复调用直接复用首次结果）；脱敏异常仅记 ERROR 不上抛，
 * 保证日志语句永不因脱敏失败阻断业务。
 */

import cn.hutool.core.text.StrBuilder;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import io.github.jevonsnotes.jevokit.core.util.JsonUtil;
import io.github.jevonsnotes.jevokit.core.util.XmlUtil;
import io.github.jevonsnotes.jevokit.desensitization.helper.DesHelper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

@Slf4j
public class LogBuilder {
    /**
     * 创建构建器。
     *
     * @return 新实例（非单例，每次构建独立）
     */
    public static LogBuilder create(){
        return new LogBuilder();
    }
    private String title;
    private Map<String,Object> metas = new LinkedHashMap<>();
    private boolean build;

    /**
     * 设置日志标题（输出时以分隔线包裹置顶）。
     *
     * @param title 标题文本
     * @return 本构建器（链式）
     */
    public LogBuilder title(String title){
        this.title = title;
        return this;
    }

    /**
     * 追加元数据项（null 键值按空串兜底，不抛错）。
     *
     * @param key   键
     * @param value 值（build 时参与日志链路脱敏）
     * @return 本构建器（链式）
     */
    public LogBuilder meta(String key,Object value){
        this.metas.put(ObjectUtil.defaultIfNull(key, StrUtil.EMPTY),ObjectUtil.defaultIfNull(value, StrUtil.EMPTY));
        return this;
    }
    /**
     * 追加 JSON 序列化的元数据项（序列化失败由 fastjson 抛出，本方法不吞）。
     *
     * @param key   键
     * @param value 对象（JSON 序列化后参与脱敏）
     * @return 本构建器（链式）
     */
    public LogBuilder metaJson(String key,Object value){
        this.metas.put(ObjectUtil.defaultIfNull(key, StrUtil.EMPTY), JSONObject.toJSONString(ObjectUtil.defaultIfNull(value, StrUtil.EMPTY)));
        return this;
    }

    /** 拼接标题与元数据为文本（不做脱敏——脱敏仅发生在 {@link #build()}）。 */
    @Override
    public String toString() {
        StrBuilder sb = new StrBuilder();
        if(StrUtil.isNotBlank(this.title)){
            sb.append("============ ").append(this.title).append( " ============\r\n");
        }

        this.metas.forEach((k,v)-> {
            sb.append(k).append(": ").append(v).append("\r\n");
        });
        return sb.toString();
    }


    /**
     * 构建脱敏日志文本：首次调用对全部元数据走日志链路脱敏并缓存结果，重复调用复用。
     *
     * @return 标题 + 键值行文本；脱敏异常仅记 ERROR，返回未脱敏降级文本（日志永不因脱敏阻断）
     */
    public String build() {
        if(!isBuild()){
            try {
                metas = (Map<String, Object>) DesHelper.desensitizeLog(metas);
            } catch (Exception e) {
                log.error("desensitization error", e);
            }
            this.build = true;
        }
        return toString();
    }

    /** 是否已 build 过（true 表示结果已缓存，再调 build 复用首次结果）。 */
    public boolean isBuild(){
        return  this.build;
    }

}
