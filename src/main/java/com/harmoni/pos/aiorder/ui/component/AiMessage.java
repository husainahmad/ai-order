package com.harmoni.pos.aiorder.ui.component;

import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;

/**
 * Left-aligned chat bubble representing an assistant message.
 * <p>
 * Renders a white bubble with dark text on the left side of the chat,
 * accompanied by a store avatar icon on the far left and a timestamp
 * showing when the reply was delivered.
 *
 * @author Husain Harmoni
 */
public class AiMessage extends VerticalLayout {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final VerticalLayout messageWrapper;

    /**
     * Creates an assistant message bubble with the given text.
     *
     * @param message the assistant's reply text
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

        messageWrapper = new VerticalLayout();
        messageWrapper.addClassName("message-bubble");
        messageWrapper.addClassName("assistant-bubble");
        messageWrapper.setPadding(false);
        messageWrapper.setSpacing(false);

        Icon avatar = new Icon(VaadinIcon.COMMENTS);
        avatar.addClassName("message-avatar");
        avatar.setSize("28px");

        Span text = new MarkdownText(message);
        text.addClassName("message-text");

        Span timestamp = new Span(LocalTime.now().format(TIME_FMT));
        timestamp.addClassName("message-timestamp");

        messageWrapper.add(text, timestamp);

        layout.add(avatar, messageWrapper);
        add(layout);
    }

    /**
     * Appends a row of quick-reply chips below this message's text.
     *
     * @param replies the suggestion labels to render as tappable chips
     * @param handler callback invoked when a chip is clicked, with the chip's label
     */
    public void addQuickReplies(List<String> replies, Consumer<String> handler) {
        if (replies == null || replies.isEmpty()) {
            return;
        }
        messageWrapper.add(new QuickReplyChips(replies, handler));
    }

    /**
     * Appends a row of quick-reply chips whose payload differs from the label
     * (e.g. a product chip labeled with its price that sends an order intent).
     *
     * @param replies the label/payload pairs to render
     * @param handler callback invoked with the clicked chip's payload
     */
    public void addQuickRepliesWithPayloads(List<QuickReplyChips.Reply> replies, Consumer<String> handler) {
        if (replies == null || replies.isEmpty()) {
            return;
        }
        messageWrapper.add(QuickReplyChips.withPayloads(replies, handler));
    }
}