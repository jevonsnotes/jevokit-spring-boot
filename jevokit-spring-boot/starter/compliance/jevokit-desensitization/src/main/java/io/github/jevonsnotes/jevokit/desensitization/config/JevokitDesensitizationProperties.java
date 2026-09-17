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
 * 脱敏模块总配置（前缀 spkit.desensitization）：算法分链路选择、属性名识别规则、
 * 级别-权限映射与忽略名单。
 * 使用方式：algorithm.view/log 分别选视图与日志链路的脱敏算法；prop-pattern 按属性名正则匹配出
 * {级别, 类型}（用于无注解场景，如 JSON/XML 原文脱敏）；level-power-key 定义每个级别对应的权限代码集，
 * 交由 SensitizationPowerInterceptor 判定当前用户可否查看明文。
 * <p>关键约束：prop-pattern 遍历顺序按 DstLevel 自然序（低编号优先命中）；
 * ignore-pattern 命中的属性名完全跳过脱敏（优先级最高）；规则写错只告警不阻断（见 DesUtil）。
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
