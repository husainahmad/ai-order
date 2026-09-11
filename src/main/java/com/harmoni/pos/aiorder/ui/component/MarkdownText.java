package com.harmoni.pos.aiorder.ui.component;

import com.vaadin.flow.component.html.Span;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import java.util.List;

/**
 * Renders markdown text as sanitized HTML inside a span.
 * <p>
 * Supports headings, lists, bold/italic, links, code, block quotes, and
 * GitHub-flavored tables. Raw HTML embedded in the source markdown is escaped
 * ({@code escapeHtml(true)}) so AI output cannot inject markup.
 *
 * @author Husain Harmoni
 */
public class MarkdownText extends Span {

    private static final Parser PARSER = Parser.builder()
            .extensions(List.of(TablesExtension.create()))
            .build();
    private static final HtmlRenderer RENDERER = HtmlRenderer.builder()
            .escapeHtml(true)
            .extensions(List.of(TablesExtension.create()))
            .build();

    /**
     * Creates a span that renders the given markdown as HTML.
     *
     * @param markdown the markdown source text
     */
    public MarkdownText(String markdown) {
        addClassName("markdown-text");
        if (markdown == null || markdown.isBlank()) {
            setText("");
            return;
        }
        Node node = PARSER.parse(markdown);
        getElement().setProperty("innerHTML", RENDERER.render(node));
    }
}