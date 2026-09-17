package io.github.jevonsnotes.jevokit.security.xss.chain;

import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.ControllerAdvice;
import io.github.jevonsnotes.jevokit.configuration.security.xss.XssProperties;
import io.github.jevonsnotes.jevokit.core.chain.StringPropertiesSupportChain;
import io.github.jevonsnotes.jevokit.configuration.holder.ConfigHolder;
import io.github.jevonsnotes.jevokit.security.xss.cleaner.XssCleaner;

/**
 * XSS 防护的字符串属性编辑链节点：在 Spring 表单绑定阶段对入站 String 属性执行 XSS 清洗。
 * 使用方式：由 jevokit 核心装配为 {@code StringPropertiesEditor}（java.beans PropertyEditor）的一环，
 * 请求参数 → Bean 属性时经 {@link #setTextEdit}，Bean 属性 → 文本输出时经 {@link #getTextEdit}；
 * 仅在容器存在 {@link XssCleaner} 实现时生效（@ConditionalOnBean）。
 * <p>检测语义：清洗动作与形态（escape/reject/clear 三模式的输出差异）由
 * {@link io.github.jevonsnotes.jevokit.security.xss.cleaner.XssCleaner} 实现定义（默认实现见
 * {@link io.github.jevonsnotes.jevokit.security.xss.cleaner.DefaultXssCleaner}）；本链只负责开关判断与
 * trim 预处理——当前请求未开启防护（ConfigHolder 的 ThreadLocal 由 XssCleanInterceptor 依
 * spkit.security.xss.enabled 与 @XssClean 注解写入）时仍执行 trim 但不清洗，行为与未引入防护一致（防御性降级）。
 * <p>关键约束：入站单向清洗（getTextEdit 原样返回）；spkit.security.xss.trim-text 对开启/关闭态均生效。
 */
@Slf4j
@RequiredArgsConstructor
@ControllerAdvice
@ConditionalOnBean(XssCleaner.class)
public class XssStringPropertiesEditChain  implements StringPropertiesSupportChain {

    private final XssCleaner xssCleaner;
    private final XssProperties properties;


    @Override
    public String getTextEdit(String text) {
        return text;
    }

    @Override
    public String setTextEdit(String text) {
        String newer = text;
        if (ConfigHolder.getXssMeta().isEnabled()) {
            newer = xssCleaner.clean(properties.isTrimText()? StrUtil.trim(newer) : newer);
            log.debug("Request parameter value:{} cleaned up by xss defending, current value is:{}.", text, newer);
        } else {
            newer = properties.isTrimText()? StrUtil.trim(newer) : newer;
        }
        return newer;
    }
}
