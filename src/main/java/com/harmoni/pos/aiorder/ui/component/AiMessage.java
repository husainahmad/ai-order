package com.harmoni.pos.aiorder.ui.component;

import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

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

        VerticalLayout messageWrapper = new VerticalLayout();
        messageWrapper.addClassName("message-bubble");
        messageWrapper.addClassName("assistant-bubble");
        messageWrapper.setPadding(false);
        messageWrapper.setSpacing(false);

        Icon avatar = new Icon(VaadinIcon.COMMENTS);
        avatar.addClassName("message-avatar");
        avatar.setSize("28px");

        Span text = new Span(message);
        text.addClassName("message-text");
        text.addClassName("pre-wrap");

        Span timestamp = new Span(LocalTime.now().format(TIME_FMT));
        timestamp.addClassName("message-timestamp");

        messageWrapper.add(text, timestamp);

        layout.add(avatar, messageWrapper);
        add(layout);
    }
}