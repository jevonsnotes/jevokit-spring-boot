package io.github.jevonsnotes.jevokit.core.editor;

import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.github.jevonsnotes.jevokit.core.chain.StringPropertiesSupportChain;

import java.beans.PropertyEditorSupport;
import java.util.Iterator;
import java.util.List;

/**
 * form表单字符串编辑器
 */
@Slf4j
@RequiredArgsConstructor
public class StringPropertiesEditor extends PropertyEditorSupport {
    private final List<StringPropertiesSupportChain> chains;
    /**
     * 出站方向（对象 → 字符串）：值依次流经全部 chain 的 getTextEdit
     *
     * @return 编辑后的字符串；原值为 null 时从空串起步过链
     */
    @Override
    public String getAsText() {
        Object value = getValue();
        Iterator<StringPropertiesSupportChain> it = chains.iterator();
        while(it.hasNext()){
            StringPropertiesSupportChain chain = it.next();
            value = chain.getTextEdit(value != null ? value.toString() : StrUtil.EMPTY);
        }
        return value.toString();
    }

    /**
     * 入站方向（字符串 → 对象）：文本依次流经全部 chain 的 setTextEdit 后 setValue
     *
     * @param text 原始文本；null 直接 setValue(null)，不过链
     */
    @Override
    public void setAsText(String text) throws IllegalArgumentException {

        if (text == null) {
            setValue(null);
        } else {
            Iterator<StringPropertiesSupportChain> it = chains.iterator();
            while(it.hasNext()){
                StringPropertiesSupportChain chain = it.next();
                text = chain.setTextEdit(text);
            }
            setValue(text);
        }
    }


}