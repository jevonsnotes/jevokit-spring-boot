package springbootkit.security.pt.chain;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import springbootkit.configuration.security.pathtraversal.PathTraversalProperties;
import springbootkit.core.chain.StringPropertiesSupportChain;
import springbootkit.configuration.holder.ConfigHolder;
import springbootkit.security.pt.util.PathTraversalUtil;

/**
 * @Description TODO
 * @Date 2022/5/24 16:04
 * @Version 0.0.1
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
