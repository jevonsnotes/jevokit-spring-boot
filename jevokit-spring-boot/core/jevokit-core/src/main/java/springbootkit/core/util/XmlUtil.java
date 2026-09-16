package springbootkit.core.util;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import springbootkit.core.lang.CallbackP;

/**
 * @Description TODO
 * @Date 2024/11/15
 * @Version 0.0.1
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
