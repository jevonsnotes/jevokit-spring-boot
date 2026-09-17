package io.github.jevonsnotes.jevokit.desensitization.config;

import lombok.Data;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import io.github.jevonsnotes.jevokit.desensitization.consts.DstAlgorithm;
import io.github.jevonsnotes.jevokit.desensitization.consts.DstLevel;
import io.github.jevonsnotes.jevokit.desensitization.consts.DstPowerCheckPeriod;
import io.github.jevonsnotes.jevokit.desensitization.consts.DstType;
import io.github.jevonsnotes.jevokit.desensitization.helper.DesHelper;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @Description TODO
 * @Date 2024/11/20
 * @Version 0.0.1
 */
@Data
@ConfigurationProperties(prefix = JevokitDesensitizationProperties.CONFIG_PREFIX)
public class JevokitDesensitizationProperties{
    protected static final String CONFIG_PREFIX = "spkit.desensitization";
    /**
     * 脱敏算法
     */
    @NestedConfigurationProperty
    private AlgorithmProperties algorithm = new AlgorithmProperties();
    /**
     * 属性脱敏识别表达式
     */
    private Map<DstLevel, Map<DstType,List<String>>> propPattern = new HashMap<>();
    /**
     * 脱敏级别与权限代码映射
     */
    private Map<DstLevel, List<String>> levelPowerKey = new HashMap<>();
    /**
     * 忽略脱敏
     */
    private List<String> ignorePattern = new ArrayList<>();
    /**
     * 检查权限的时期。ENCRYPT加密时检查，有权限返回明文；DECRYPT 解密时检查，加密时直接返回密文
     */
    private DstPowerCheckPeriod powerCheckPeriod = DstPowerCheckPeriod.DECRYPT;
}
