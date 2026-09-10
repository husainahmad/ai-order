package com.harmoni.pos.aiorder.ui.component;

import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Left-aligned chat bubble representing the AI assistant's response.
 * <p>
 * Supports two rendering modes:
 * <ul>
 *   <li><strong>Plain text</strong> — rendered as a {@link Span} with pre-wrap whitespace</li>
 *   <li><strong>Markdown table</strong> — detected by pipe and separator characters, rendered as a
 *       styled HTML {@code <table>} with header row, number column centering, and price column
 *       right-alignment</li>
 * </ul>
 * Basic {@code **bold**} markdown is also supported in table cells.
 *
 * @author Husain Harmoni
 */
public class AiMessage extends VerticalLayout {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final VerticalLayout messageWrapper;
    private String renderedText;

    /**
     * Creates an AI chat bubble with the given message text.
     *
     * @param message the AI response text, which may contain markdown tables
     */
    public AiMessage(String message) {
        addClassName("assistant-message");
        setWidthFull();
        setPadding(false);
        setSpacing(false);

        HorizontalLayout layout = new HorizontalLayout();
        layout.setWidthFull();
        layout.setJustifyContentMode(JustifyContentMode.START);
        layout.setDefaultVerticalComponentAlignment(Alignment.START);
        layout.setSpacing(true);

        Icon avatar = new Icon(VaadinIcon.COMMENTS);
        avatar.addClassName("message-avatar");
        avatar.setSize("28px");

        messageWrapper = new VerticalLayout();
        messageWrapper.addClassName("message-bubble");
        messageWrapper.addClassName("assistant-bubble");
        messageWrapper.setPadding(false);
        messageWrapper.setSpacing(false);

        setMessage(message);

        Span timestamp = new Span(LocalTime.now().format(TIME_FMT));
        timestamp.addClassName("message-timestamp");

        layout.add(avatar, messageWrapper);
        add(layout);
        add(timestamp);
    }

    /**
     * Renders the message content, choosing between plain text and markdown table
     * rendering based on the message structure.
     *
     * @param message the raw message text
     */
    private void setMessage(String message) {
        messageWrapper.removeAll();
        if (message != null && isMarkdownTable(message)) {
            String html = "<div class=\"message-text\">" + renderMarkdown(message) + "</div>";
            renderedText = html;
            messageWrapper.add(new com.vaadin.flow.component.Html(html));
        } else {
            Span textSpan = new Span(message != null ? message : "");
            textSpan.addClassName("message-text");
            textSpan.addClassName("pre-wrap");
            renderedText = message != null ? message : "";
            messageWrapper.add(textSpan);
        }
    }

    /**
     * Heuristic to detect markdown table content: requires pipe characters,
     * a separator row ({@code ---}), and at least 6 pipe-delimited segments.
     *
     * @param msg the message text to check
     * @return {@code true} if the message appears to contain a markdown table
     */
    private boolean isMarkdownTable(String msg) {
        return msg.contains("|") && msg.contains("---") && msg.split("\\|").length >= 6;
    }

    /**
     * Renders markdown content to HTML. Converts pipe-delimited tables into styled
     * {@code <table>} elements with header, body, and special column classes for
     * number and price alignment. Non-table lines are wrapped in {@code <div>} elements.
     *
     * @param raw the raw markdown text
     * @return rendered HTML string
     */
    private String renderMarkdown(String raw) {
        String escaped = escapeHtml(raw);
        escaped = escaped.replaceAll("\\*\\*(.+?)\\*\\*", "<strong>$1</strong>");
        String[] lines = escaped.split("\n");
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < lines.length) {
            String t = lines[i].trim();
            boolean isRow = t.startsWith("|") && t.endsWith("|");
            boolean isNextSep = i + 1 < lines.length && isSeparator(lines[i + 1].trim());
            if (isRow && isNextSep) {
                String[] header = splitRow(t);
                out.append("<div class=\"menu-table-wrapper\"><table class=\"menu-table\"><thead><tr>");
                for (String h : header) out.append("<th>").append(h.trim()).append("</th>");
                out.append("</tr></thead><tbody>");
                i += 2;
                while (i < lines.length) {
                    String r = lines[i].trim();
                    if (!r.startsWith("|") || !r.endsWith("|")) break;
                    if (isSeparator(r)) { i++; continue; }
                    String[] cells = splitRow(r);
                    out.append("<tr>");
                    for (int c = 0; c < cells.length; c++) {
                        String cell = cells[c].trim();
                        String cls = "";
                        if (c == 0 && cell.matches("\\d+")) cls = " class=\"num-col\"";
                        else if (cell.contains("Rp")) cls = " class=\"price-col\"";
                        out.append("<td").append(cls).append(">").append(cell).append("</td>");
                    }
                    out.append("</tr>");
                    i++;
                }
                out.append("</tbody></table></div>");
            } else {
                if (!t.isEmpty()) out.append("<div class=\"menu-text-line\">").append(t).append("</div>");
                else out.append("<div style=\"height:0.25rem\"></div>");
                i++;
            }
        }
        String html = out.toString();
        if (!html.contains("<table")) {
            html = escaped.replace("\n", "<br/>");
        }
        return html;
    }

    /**
     * Checks if a line is a markdown table separator row ({@code |---|---|}).
     *
     * @param line the trimmed line to check
     * @return {@code true} if the line is a separator row
     */
    private boolean isSeparator(String line) {
        String t = line.trim();
        return t.startsWith("|") && t.replaceAll("[|\\-:\\s]", "").isEmpty();
    }

    /**
     * Splits a pipe-delimited row into cell values.
     *
     * @param row a row like {@code | cell1 | cell2 |}
     * @return array of cell values (may contain empty strings)
     */
    private String[] splitRow(String row) {
        String inner = row.substring(1, row.length() - 1);
        return inner.split("\\|", -1);
    }

    /**
     * Escapes HTML special characters to prevent XSS.
     *
     * @param s the raw string
     * @return HTML-safe string
     */
    private String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * Replaces the current message content with new text.
     *
     * @param message the new message text to render
     */
    public void updateText(String message) {
        setMessage(message);
    }

    /**
     * Returns the rendered content of this message: the plain text for text
     * messages, or the generated HTML (including the markdown table markup)
     * for table messages.
     *
     * @return the rendered message content
     */
    public String getText() {
        return renderedText != null ? renderedText : "";
    }
}
