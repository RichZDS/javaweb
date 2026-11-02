package com.richzds.quiz.richtext;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Code;
import org.commonmark.node.CustomNode;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Node;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.NodeRenderer;
import org.commonmark.renderer.html.HtmlNodeRendererContext;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.renderer.html.HtmlWriter;
import org.springframework.stereotype.Component;

/**
 * Markdown → HTML。原始 HTML 一律转义（escapeHtml），链接和图片地址过滤 javascript: 等危险协议，
 * 所以输出里只会出现 commonmark 自己生成的标准标签，相当于标签白名单。
 * 代码块输出带内联样式的 {@code <pre><code>}，并对 java / c / python / sql 做简单高亮。
 */
@Component
public class RichTextRenderer {

    static final String PRE_STYLE = "font-family:Menlo,Consolas,'Courier New',monospace;"
            + "background:#f6f8fa;color:#24292e;padding:12px;border-radius:6px;overflow-x:auto;"
            + "white-space:pre;font-size:13px;line-height:1.5;margin:8px 0;";
    static final String INLINE_CODE_STYLE = "font-family:Menlo,Consolas,'Courier New',monospace;"
            + "background:#f6f8fa;padding:1px 4px;border-radius:3px;font-size:0.9em;";

    private static final int CACHE_SIZE = 4000;

    private final Parser parser;
    private final HtmlRenderer htmlRenderer;
    private final Map<String, String> htmlCache = Collections.synchronizedMap(
            new LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > CACHE_SIZE;
                }
            });

    public RichTextRenderer() {
        List<Extension> extensions = List.of(TablesExtension.create());
        this.parser = Parser.builder().extensions(extensions).build();
        this.htmlRenderer = HtmlRenderer.builder()
                .extensions(extensions)
                .escapeHtml(true)
                .sanitizeUrls(true)
                .nodeRendererFactory(CodeRenderer::new)
                .build();
    }

    /** 渲染 Markdown；null 返回 null。 */
    public String toHtml(String markdown) {
        if (markdown == null) {
            return null;
        }
        String cached = htmlCache.get(markdown);
        if (cached != null) {
            return cached;
        }
        String html = htmlRenderer.render(parser.parse(markdown)).trim();
        htmlCache.put(markdown, html);
        return html;
    }

    /** 去掉格式后的纯文本前 maxChars 个字（按 Unicode 字符计），空白合并成一个空格。 */
    public String preview(String markdown, int maxChars) {
        if (markdown == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        parser.parse(markdown).accept(new PlainTextCollector(sb));
        String text = sb.toString().replaceAll("\\s+", " ").trim();
        if (text.codePointCount(0, text.length()) <= maxChars) {
            return text;
        }
        return text.substring(0, text.offsetByCodePoints(0, maxChars));
    }

    /** 收集纯文本：保留文字和代码原文，块与块之间、换行处补空格。 */
    private static final class PlainTextCollector extends AbstractVisitor {

        private final StringBuilder sb;

        PlainTextCollector(StringBuilder sb) {
            this.sb = sb;
        }

        @Override
        public void visit(Text text) {
            sb.append(text.getLiteral());
        }

        @Override
        public void visit(Code code) {
            sb.append(code.getLiteral());
        }

        @Override
        public void visit(FencedCodeBlock block) {
            sb.append(' ').append(block.getLiteral()).append(' ');
        }

        @Override
        public void visit(IndentedCodeBlock block) {
            sb.append(' ').append(block.getLiteral()).append(' ');
        }

        @Override
        public void visit(HtmlInline html) {
            sb.append(html.getLiteral());
        }

        @Override
        public void visit(HtmlBlock html) {
            sb.append(' ').append(html.getLiteral()).append(' ');
        }

        @Override
        public void visit(SoftLineBreak lineBreak) {
            sb.append(' ');
        }

        @Override
        public void visit(HardLineBreak lineBreak) {
            sb.append(' ');
        }

        @Override
        public void visit(Paragraph paragraph) {
            visitChildren(paragraph);
            sb.append(' ');
        }

        @Override
        public void visit(Heading heading) {
            visitChildren(heading);
            sb.append(' ');
        }

        @Override
        public void visit(CustomNode node) {
            visitChildren(node);
            sb.append(' ');
        }
    }

    /** 代码块和行内代码的渲染：内联样式 + 高亮。 */
    private static final class CodeRenderer implements NodeRenderer {

        private final HtmlWriter html;

        CodeRenderer(HtmlNodeRendererContext context) {
            this.html = context.getWriter();
        }

        @Override
        public Set<Class<? extends Node>> getNodeTypes() {
            return Set.of(FencedCodeBlock.class, IndentedCodeBlock.class, Code.class);
        }

        @Override
        public void render(Node node) {
            if (node instanceof Code code) {
                html.raw("<code style=\"" + INLINE_CODE_STYLE + "\">");
                html.raw(CodeHighlighter.escape(code.getLiteral()));
                html.raw("</code>");
                return;
            }
            String literal;
            String language = null;
            if (node instanceof FencedCodeBlock fenced) {
                literal = fenced.getLiteral();
                String info = fenced.getInfo();
                if (info != null && !info.isBlank()) {
                    language = info.trim().split("\\s+")[0];
                }
            } else {
                literal = ((IndentedCodeBlock) node).getLiteral();
            }
            // 去掉末尾换行，避免 pre 里多出一个空行
            if (literal.endsWith("\n")) {
                literal = literal.substring(0, literal.length() - 1);
            }
            html.line();
            html.raw("<pre style=\"" + PRE_STYLE + "\"><code>");
            html.raw(CodeHighlighter.highlight(literal, language));
            html.raw("</code></pre>");
            html.line();
        }
    }
}
