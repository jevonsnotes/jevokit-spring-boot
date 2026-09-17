package io.github.jevonsnotes.jevokit.desensitization;

import cn.hutool.extra.spring.SpringUtil;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import io.github.jevonsnotes.jevokit.desensitization.config.JevokitDesensitizationProperties;
import io.github.jevonsnotes.jevokit.desensitization.consts.DstLevel;
import io.github.jevonsnotes.jevokit.desensitization.consts.DstType;
import io.github.jevonsnotes.jevokit.desensitization.helper.DesHelper;
import io.github.jevonsnotes.jevokit.desensitization.interceptor.impl.DefaultSensitizationPowerInterceptor;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * desensitization 测试公共基座：
 * 1. fixtures 从 classpath 读取（src/test/resources/fixtures/desensitization/）；
 * 2. DesUtil 的静态依赖（SpringUtil/JevokitDesensitizationProperties/DesHelper）
 *    经 ApplicationContextRunner 装配真实上下文，测试运行期与生产同通路。
 */
public final class DesensitizationTestSupport {

    /** 演示用 SM4 密钥（16 字节，与原 SimpleTest 草稿一致） */
    public static final String TEST_SM4_KEY = "0123456789abcdef";

    private DesensitizationTestSupport() {
    }

    /** 读取 fixtures/desensitization/ 下的样例文件（classpath，无外部文件系统依赖） */
    public static String fixture(String name) {
        try (InputStream in = DesensitizationTestSupport.class
                .getResourceAsStream("/fixtures/desensitization/" + name)) {
            if (in == null) {
                throw new IllegalStateException("fixture not found on classpath: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("fixture read failed: " + name, e);
        }
    }

    /** 构造带 propPattern/ignorePattern/levelPowerKey 的 properties（编程式，等价 yml 绑定结果） */
    public static JevokitDesensitizationProperties properties(
            Map<DstLevel, Map<DstType, List<String>>> propPattern,
            List<String> ignorePattern,
            Map<DstLevel, List<String>> levelPowerKey) {
        JevokitDesensitizationProperties properties = new JevokitDesensitizationProperties();
        properties.setPropPattern(propPattern);
        properties.setIgnorePattern(ignorePattern);
        properties.setLevelPowerKey(levelPowerKey);
        return properties;
    }

    /** 满足 DesUtil 静态依赖的最小上下文（SM4 密钥走 spkit.crypto.sm4-key 配置键） */
    public static ApplicationContextRunner minimalRunner(JevokitDesensitizationProperties properties) {
        return new ApplicationContextRunner()
                .withPropertyValues("spkit.crypto.sm4-key=" + TEST_SM4_KEY)
                .withBean(SpringUtil.class)
                .withBean(JevokitDesensitizationProperties.class, () -> properties)
                .withBean(DesHelper.class,
                        () -> new DesHelper(properties, new DefaultSensitizationPowerInterceptor()));
    }
}
