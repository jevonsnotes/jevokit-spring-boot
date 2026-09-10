package springbootkit.core.editor;

import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import springbootkit.core.chain.StringPropertiesSupportChain;

import java.beans.PropertyEditorSupport;
import java.util.Iterator;
import java.util.List;

/**
 * @Description form表单字符串编辑器
 * @Date 2022/5/24 16:11
 * @Version 0.0.1
 */
@Slf4j
@RequiredArgsConstructor
public class StringPropertiesEditor extends PropertyEditorSupport {
    private final List<StringPropertiesSupportChain> chains;
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