package com.harmoni.pos.aiorder.ui.component;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.KeyModifier;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextArea;

/**
 * Message composer bar pinned to the bottom of the order chat view.
 * <p>
 * Features:
 * <ul>
 *   <li>Multi-line {@link TextArea} with placeholder text</li>
 *   <li><strong>Enter</strong> sends the message; <strong>Shift+Enter</strong> inserts a newline</li>
 *   <li>Round send button with an upward arrow icon</li>
 *   <li>Disabled state while waiting for an AI response</li>
 * </ul>
 *
 * @author Husain Harmoni
 */
public class ChatInput extends HorizontalLayout {

    /** Callback interface for when the user submits a message. */
    public interface SendListener {
        /**
         * Called with the trimmed message text when the user sends.
         *
         * @param message the non-empty, trimmed message text
         */
        void onSend(String message);
    }

    private final TextArea textArea;
    private final Button sendButton;
    private boolean waitingForResponse = false;
    private SendListener sendListener;

    /** Creates the composer with the text area, Enter-to-send handling, and send button. */
    public ChatInput() {
        addClassName("chat-input");
        setWidthFull();
        setDefaultVerticalComponentAlignment(Alignment.END);
        setSpacing(true);

        textArea = new TextArea();
        textArea.setPlaceholder("Tulis pesan...");
        textArea.addClassName("chat-textarea");
        textArea.setMinHeight("42px");
        textArea.setMaxHeight("120px");
        textArea.setClearButtonVisible(false);
        textArea.setWidthFull();

        textArea.addKeyDownListener(Key.ENTER, e -> {
            if (!e.getModifiers().contains(KeyModifier.SHIFT)) {
                sendMessage();
            }
        });

        sendButton = new Button(new Icon(VaadinIcon.ARROW_UP));
        sendButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
        sendButton.addClassName("send-button");
        sendButton.setAriaLabel("Kirim");
        sendButton.addClickListener(e -> sendMessage());
        sendButton.setDisableOnClick(false);

        add(textArea, sendButton);
        setFlexGrow(1, textArea);
    }

    /**
     * Registers a listener that will be called when the user sends a message.
     *
     * @param listener the callback to invoke with the message text
     */
    public void setSendListener(SendListener listener) {
        this.sendListener = listener;
    }

    /**
     * Validates, trims, and dispatches the current input through the send listener,
     * then clears the text area and refocuses it.
     */
    private void sendMessage() {
        String message = textArea.getValue();
        if (message == null || message.trim().isEmpty() || waitingForResponse) {
            return;
        }
        message = message.trim();
        textArea.clear();
        textArea.focus();
        if (sendListener != null) {
            sendListener.onSend(message);
        }
    }

    /**
     * Toggles the waiting-for-response state. When waiting, both the text area
     * and send button are disabled to prevent duplicate submissions.
     *
     * @param waiting {@code true} to disable input while the AI is responding
     */
    public void setWaitingForResponse(boolean waiting) {
        this.waitingForResponse = waiting;
        boolean enabled = isEnabled() && !waiting;
        sendButton.setEnabled(enabled);
        textArea.setEnabled(enabled);
    }

    /**
     * Returns whether the chat is currently awaiting an AI response.
     *
     * @return {@code true} while input and the send button are disabled
     */
    public boolean isWaitingForResponse() {
        return waitingForResponse;
    }

    /** {@inheritDoc} */
    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        boolean childEnabled = enabled && !waitingForResponse;
        textArea.setEnabled(childEnabled);
        sendButton.setEnabled(childEnabled);
    }

    /** Sets focus to the text area for immediate typing. */
    public void focus() {
        textArea.focus();
    }
}
