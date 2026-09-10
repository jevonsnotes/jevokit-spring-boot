package springbootkit.core.util;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.ClassUtil;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import lombok.extern.slf4j.Slf4j;

/**
 * @Description TODO
 * @Date 2024/11/19
 * @Version 0.0.1
 */
@Slf4j
public class CloneUtil {

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
