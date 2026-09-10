package springbootkit.desensitization.builder.log;

/**
 * @Description TODO
 * @Date 2024/11/22
 * @Version 0.0.1
 */

import cn.hutool.core.text.StrBuilder;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import springbootkit.core.util.JsonUtil;
import springbootkit.core.util.XmlUtil;
import springbootkit.desensitization.helper.DesHelper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * @Description
 * @Date 2024/1/30 14:12
 * @Version 0.0.1
 */
@Slf4j
public class LogBuilder {
    public static LogBuilder create(){
        return new LogBuilder();
    }
    private String title;
    private Map<String,Object> metas = new LinkedHashMap<>();
    private boolean build;

    public LogBuilder title(String title){
        this.title = title;
        return this;
    }

    public LogBuilder meta(String key,Object value){
        this.metas.put(ObjectUtil.defaultIfNull(key, StrUtil.EMPTY),ObjectUtil.defaultIfNull(value, StrUtil.EMPTY));
        return this;
    }
    public LogBuilder metaJson(String key,Object value){
        this.metas.put(ObjectUtil.defaultIfNull(key, StrUtil.EMPTY), JSONObject.toJSONString(ObjectUtil.defaultIfNull(value, StrUtil.EMPTY)));
        return this;
    }

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

    public boolean isBuild(){
        return  this.build;
    }

}
