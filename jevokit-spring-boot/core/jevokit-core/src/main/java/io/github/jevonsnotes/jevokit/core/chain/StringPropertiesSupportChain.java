package io.github.jevonsnotes.jevokit.core.chain;

/**
 * 字符串属性编辑链 SPI：实现类注册为 Spring Bean 后，由装配层经 StringPropertiesEditor 串成链，
 * 拦截 Spring 属性绑定过程中字符串值的进出两个方向。
 * <p>方向约定：getTextEdit 处理"字符串 → 对象"（请求参数绑定/反序列化入站）；setTextEdit 处理"对象 → 字符串"（序列化出站）。
 * 安全类 starter（XSS/SQL 注入/路径穿越清洗）即通过实现本接口挂载清洗逻辑；不需要处理的方向返回原值即可。
 */
public interface StringPropertiesSupportChain {

    /**
     * 入站方向编辑（字符串 → 对象）
     *
     * @param text 原字符串
     * @return 编辑后的字符串
     */
    String getTextEdit(String text) ;

    /**
     * 出站方向编辑（对象 → 字符串）
     *
     * @param text 原字符串
     * @return 编辑后的字符串
     */
    String setTextEdit(String text) ;
}
