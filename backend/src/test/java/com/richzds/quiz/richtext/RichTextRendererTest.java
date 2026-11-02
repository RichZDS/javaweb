package com.richzds.quiz.richtext;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RichTextRendererTest {

    private final RichTextRenderer renderer = new RichTextRenderer();

    @Test
    void rawHtmlIsEscaped() {
        String html = renderer.toHtml("hello <script>alert(1)</script>\n\n<div onclick=\"x()\">block</div>");
        assertThat(html).doesNotContain("<script>").doesNotContain("<div");
        assertThat(html).contains("&lt;script&gt;alert(1)&lt;/script&gt;");
        assertThat(html).contains("&lt;div onclick=");
    }

    @Test
    void dangerousLinksAreSanitized() {
        String html = renderer.toHtml("[点我](javascript:alert(1)) ![x](javascript:alert(2))");
        assertThat(html).doesNotContain("javascript:");
    }

    @Test
    void fencedCodeBlockHasInlineStyleAndHighlighting() {
        String html = renderer.toHtml("```java\n// 注释\nint x = 5; String s = \"<b>\";\n```");
        assertThat(html).startsWith("<pre style=\"font-family:");
        assertThat(html).contains("overflow-x:auto").contains("background:#f6f8fa");
        assertThat(html).contains("<code>");
        assertThat(html).contains("<span style=\"" + CodeHighlighter.KEYWORD + "\">int</span>");
        assertThat(html).contains("<span style=\"" + CodeHighlighter.COMMENT + "\">// 注释</span>");
        assertThat(html).contains("<span style=\"" + CodeHighlighter.NUMBER + "\">5</span>");
        assertThat(html).as("字符串里的标签也被转义")
                .contains("<span style=\"" + CodeHighlighter.STRING + "\">&quot;&lt;b&gt;&quot;</span>");
        assertThat(html).doesNotContain("class=");
    }

    @Test
    void highlightsPythonCAndSql() {
        assertThat(renderer.toHtml("```python\ndef f():  # c\n    return None\n```"))
                .contains(">def</span>").contains(">None</span>").contains("<span style=\"" + CodeHighlighter.COMMENT + "\"># c</span>");
        assertThat(renderer.toHtml("```c\n#include <stdio.h>\nint main() { return 0; }\n```"))
                .contains("<span style=\"" + CodeHighlighter.DIRECTIVE + "\">#include &lt;stdio.h&gt;</span>")
                .contains(">return</span>");
        assertThat(renderer.toHtml("```sql\nselect name from t where id = 'a''b' -- x\n```"))
                .contains(">select</span>").contains(">where</span>")
                .contains("<span style=\"" + CodeHighlighter.STRING + "\">&#39;a&#39;&#39;b&#39;</span>")
                .contains("<span style=\"" + CodeHighlighter.COMMENT + "\">-- x</span>");
    }

    @Test
    void unknownLanguageIsOnlyEscaped() {
        String html = renderer.toHtml("```\nint <x>\n```");
        assertThat(html).contains("<pre style=").contains("int &lt;x&gt;").doesNotContain("<span");
    }

    @Test
    void inlineCodeIsStyled() {
        assertThat(renderer.toHtml("用 `a<b` 比较")).contains("<code style=\"font-family:").contains("a&lt;b");
    }

    @Test
    void tablesAreSupported() {
        assertThat(renderer.toHtml("| a | b |\n|---|---|\n| 1 | 2 |")).contains("<table>").contains("<td>1</td>");
    }

    @Test
    void previewStripsFormattingAndTruncates() {
        assertThat(renderer.preview("**加粗** 和 `code`\n\n第二段", 80)).isEqualTo("加粗 和 code 第二段");
        assertThat(renderer.preview("一二三四五六", 3)).isEqualTo("一二三");
        assertThat(renderer.preview(null, 80)).isEmpty();
    }

    @Test
    void nullStaysNull() {
        assertThat(renderer.toHtml(null)).isNull();
    }
}
