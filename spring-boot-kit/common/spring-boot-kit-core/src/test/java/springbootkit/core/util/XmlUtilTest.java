package springbootkit.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link XmlUtil} 单元测试：XML 格式判断与节点编辑。
 */
@DisplayName("XmlUtil 格式判断与节点编辑")
class XmlUtilTest {

    // ------------------------------------------------------------------
    // isXml
    // ------------------------------------------------------------------

    @Test
    @DisplayName("isXml：成对标签与自闭合标签均识别为 XML")
    void isXmlAcceptsValidXml() {
        // expect
        assertThat(XmlUtil.isXml("<a><b>1</b></a>")).as("成对标签应识别为 XML").isTrue();
        assertThat(XmlUtil.isXml("<a/>")).as("自闭合标签应识别为 XML").isTrue();
        assertThat(XmlUtil.isXml("<a attr=\"v\"/>")).as("带属性的自闭合标签应识别为 XML").isTrue();
        assertThat(XmlUtil.isXml("<a><b/></a>")).as("嵌套自闭合应识别为 XML").isTrue();
    }

    @Test
    @DisplayName("isXml：普通文本、无闭合标签、空串均不是 XML")
    void isXmlRejectsNonXml() {
        // expect
        assertThat(XmlUtil.isXml("hello world")).as("纯文本不是 XML").isFalse();
        assertThat(XmlUtil.isXml("<a>")).as("只有开标签不是 XML").isFalse();
        assertThat(XmlUtil.isXml("<a>1")).as("未闭合不是 XML").isFalse();
        assertThat(XmlUtil.isXml("")).as("空串不是 XML").isFalse();
        assertThat(XmlUtil.isXml(null)).as("null 不是 XML").isFalse();
    }

    @Test
    @DisplayName("isXml：对入参做 trim，前后空白不影响判断")
    void isXmlTrimsInput() {
        assertThat(XmlUtil.isXml("  <a><b/></a>  ")).as("首尾空白应被忽略").isTrue();
    }

    // ------------------------------------------------------------------
    // editNode(String, CallbackP)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("editNode(String)：文本叶子节点的值被回调编辑并序列化返回")
    void editNodeStringEditsLeafText() {
        // given
        String xml = "<root><a>1</a><b>2</b></root>";
        // when：给每个叶子文本追加后缀
        String result = XmlUtil.editNode(xml, node -> {
            if (!"#text".equals(node.getNodeName()) && node.getTextContent() != null) {
                node.setTextContent(node.getTextContent() + "-x");
            }
        });
        // then
        assertThat(result).as("叶子 a 的文本应被编辑").contains("<a>1-x</a>");
        assertThat(result).as("叶子 b 的文本应被编辑").contains("<b>2-x</b>");
        assertThat(result).as("根节点应保留").contains("<root");
    }

    @Test
    @DisplayName("editNode(String)：空元素节点（自闭合）也进入回调")
    void editNodeStringEditsEmptyElement() {
        // given：<c/> 无子节点，应走空元素分支
        String xml = "<root><a>1</a><c/></root>";
        // when
        String result = XmlUtil.editNode(xml, node -> {
            if ("c".equals(node.getNodeName())) {
                node.setTextContent("filled");
            }
        });
        // then
        assertThat(result).as("空元素 c 应被回调写入内容").contains("<c>filled</c>");
    }

    @Test
    @DisplayName("editNode(Node)：直接在 Document 对象上编辑，不返回字符串")
    void editNodeDocumentInPlace() {
        // given
        Document document = XmlUtil.parseXml("<root><v>old</v></root>");
        // when
        XmlUtil.editNode((Node) document, node -> {
            if ("v".equals(node.getNodeName())) {
                node.setTextContent("new");
            }
        });
        // then
        assertThat(XmlUtil.toStr(document)).contains("<v>new</v>");
    }

    @Test
    @DisplayName("editNode(String)：回调不匹配任何节点时原文语义不变")
    void editNodeNoMatchKeepsContent() {
        // given / when
        String result = XmlUtil.editNode("<root><a>1</a></root>", node -> { /* 无操作 */ });
        // then
        assertThat(result).contains("<a>1</a>").as("无操作回调不应改变文档内容");
    }
}
