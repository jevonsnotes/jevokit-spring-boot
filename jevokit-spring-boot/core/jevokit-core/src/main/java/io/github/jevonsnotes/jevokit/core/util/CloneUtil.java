package io.github.jevonsnotes.jevokit.core.util;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.ClassUtil;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import lombok.extern.slf4j.Slf4j;

/**
 * 深克隆工具：优先走 Java 序列化克隆，失败时降级为 fastjson2 序列化克隆。
 * <p>常用入口：{@link #deep(Object)}。
 * <p>关键约束：首选路径依赖对象实现 Serializable，建议数据载体一律实现该接口；
 * 未实现（或流式克隆抛错）时自动降级为"带类型信息的 JSON 往返"，要求对象可被 fastjson2
 * 序列化且字段类型可实例化，transient/不可序列化的部分会丢失。降级仅记 debug 日志，不告警。
 */
@Slf4j
public class CloneUtil {

    /**
     * 深克隆对象
     *
     * @param obj 原对象，可为 null
     * @param <T> 对象类型
     * @return 克隆副本；序列化与 JSON 两条路径均不可用时返回 null
     */
    public static <T>T deep(T obj) {
        //ObjectUtil.cloneByStream依赖Serializable接口，建议将数据载体实现Serializable接口，否则将触发json实现深度复制
        T newObj = null;
        try {
            newObj = ObjectUtil.cloneByStream(obj);
        } catch (Exception e) {
            // nothing output
            log.debug("流式克隆失败，将使用json方式深克隆",e);
        }
        if(newObj == null){
            String jsonString = JSONObject.toJSONString(obj, JSONWriter.Feature.WriteClassName);
            newObj = JSONObject.parseObject(jsonString, ClassUtil.getClass(obj), JSONReader.Feature.SupportAutoType);
        }
        return newObj;
    }


}
