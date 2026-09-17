package io.github.jevonsnotes.jevokit.core.util;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import io.github.jevonsnotes.jevokit.core.lang.CallbackP;

/**
 * XML 处理工具，在 hutool XmlUtil 基础上补充轻量格式探测与叶子节点遍历编辑能力。
 * <p>常用入口：{@link #isXml(String)} 做格式预判（只看首尾标签与闭合特征，不保证结构合法）；
 * {@link #editNode(String, CallbackP)} 解析报文后对叶子节点逐个回调修改，再序列化返回新报文。
 * <p>遍历只回调"叶子"节点（唯一子节点为文本的元素、或无子节点的非文本节点），中间节点不触发回调；
 * DOMException 统一捕获记日志、不上抛，避免单个坏节点中断整棵树的编辑。
 */
@Slf4j
public class XmlUtil extends cn.hutool.core.util.XmlUtil {
    /**
     * 判断是否xml格式
     * @param content 文本
     * @return 返回判断结果
     */
    public static boolean isXml(String content){
        content = StrUtil.trimToEmpty(content);
        boolean res = StrUtil.isWrap(content, "<", ">");
        res = res && (content.contains("</") || content.contains("/>"));
        return res;
    }

    /**
     * 编辑node
     * @param root 节点
     * @param callback 回调函数
     */
    public static void editNode(Node root, CallbackP<Node> callback){
        iterateNodes(root, callback);
    }
    /**
     * 编辑node
     * @param xml 报文体
     * @param callback 回调函数
     * @return 全部节点编辑完成后的新报文
     */
    public static String editNode(String xml, CallbackP<Node> callback){
        Document document = parseXml(xml);
        iterateNodes(document, callback);
        return toStr(document);
    }

    private static void iterateNodes(Node root, CallbackP<Node> callback) {
        try {
            if (root.hasChildNodes()) {
                if (!root.getFirstChild().hasChildNodes() && root.getFirstChild().getNextSibling() == null) {
                    //最后一个节点
                    callback.call(root);
                } else {
                    iterateNodes(root.getFirstChild(), callback);
                }

            } else if (!"#text".equals(root.getNodeName()) && StrUtil.isBlank(root.getNodeValue())) {
                callback.call(root);
            }

            //判断是否存在相邻节点
            if (root.getNextSibling() != null) {
                iterateNodes(root.getNextSibling(), callback);
            }
        } catch (DOMException e) {
            log.error("xml解析出错：{}", e.getMessage(), e);
        }
    }
}
