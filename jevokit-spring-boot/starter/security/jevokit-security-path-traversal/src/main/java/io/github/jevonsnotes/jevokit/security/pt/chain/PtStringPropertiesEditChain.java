package io.github.jevonsnotes.jevokit.security.pt.chain;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.github.jevonsnotes.jevokit.configuration.security.pathtraversal.PathTraversalProperties;
import io.github.jevonsnotes.jevokit.core.chain.StringPropertiesSupportChain;
import io.github.jevonsnotes.jevokit.configuration.holder.ConfigHolder;
import io.github.jevonsnotes.jevokit.security.pt.util.PathTraversalUtil;

/**
 * 路径穿越防护的字符串属性编辑链节点：在 Spring 表单绑定阶段对入站 String 属性执行路径穿越清洗。
 * 使用方式：由 jevokit 核心装配为 {@code StringPropertiesEditor}（java.beans PropertyEditor）的一环，
 * 请求参数 → Bean 属性时经 {@link #setTextEdit}，Bean 属性 → 文本输出时经 {@link #getTextEdit}。
 * <p>检测语义：仅在当前请求开关开启（ConfigHolder 的 ThreadLocal 由 PathTraversalInterceptor 依
 * spkit.security.path-traversal.enabled 与 @PtClean 注解写入）时清洗，否则原样透传——保证关闭态/被
 * @PtClean 忽略的接口行为与未引入本模块完全一致（防御性降级，不吞参数也不报错）。
 * <p>关键约束：入站单向清洗（getTextEdit 原样返回），出站内容不做二次处理；
 * 清洗规则与模式语义见 {@link io.github.jevonsnotes.jevokit.security.pt.util.PathTraversalUtil}。
 */
@Slf4j
@RequiredArgsConstructor
public class PtStringPropertiesEditChain implements StringPropertiesSupportChain {

    private final PathTraversalProperties properties;

    @Override
    public String getTextEdit(String content) {
        return content;
    }

    @Override
    public String setTextEdit(String content) {
        String newer = content;
        if(ConfigHolder.getPtMeta().isEnabled()) {
            newer = PathTraversalUtil.clean(content,properties.getAllTaintedPatterns());
            log.debug("Request parameter value:{} cleaned up by path-traversal defending, current value is:{}.", content, newer);
        }
        return newer;
    }
}
