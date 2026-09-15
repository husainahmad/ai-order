package com.harmoni.pos.aiorder.ui.component;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

import java.util.List;
import java.util.function.Consumer;

/**
 * A row of tappable suggestion chips rendered below an assistant message.
 * <p>
 * Each chip carries a short reply label; clicking it invokes the supplied
 * handler (typically the chat's send callback) with that label as the
 * message text, so the customer can answer without typing.
 *
 * @author Husain Harmoni
 */
public class QuickReplyChips extends HorizontalLayout {

    /**
     * A chip entry pairing a visible label with the message text to send.
     *
     * @param label   the text shown on the chip
     * @param payload the text dispatched to the handler when the chip is tapped
     */
    public record Reply(String label, String payload) {}

    /**
     * Creates the chip row from the given reply labels, sending the label
     * itself as the message.
     *
     * @param replies the short suggestion labels to render as chips
     * @param handler callback invoked with the clicked chip's label
     */
    public QuickReplyChips(List<String> replies, Consumer<String> handler) {
        addClassName("quick-reply-chips");
        setWidthFull();
        setSpacing(true);

        for (String reply : replies) {
            addChip(reply, reply, handler);
        }
    }

    /**
     * Creates a chip row from label/payload pairs, sending the payload as the
     * message. A static factory is used to avoid an erasure clash with the
     * {@link List}<{@link String}> constructor.
     *
     * @param replies the chips to render
     * @param handler callback invoked with the clicked chip's payload
     * @return the configured chip row
     */
    public static QuickReplyChips withPayloads(List<Reply> replies, Consumer<String> handler) {
        QuickReplyChips chips = new QuickReplyChips(List.of(), handler);
        for (Reply reply : replies) {
            chips.addChip(reply.label(), reply.payload(), handler);
        }
        return chips;
    }

    private void addChip(String label, String payload, Consumer<String> handler) {
        Button chip = new Button(label);
        chip.addClassName("quick-reply-chip");
        chip.addClickListener(e -> {
            if (handler != null) {
                handler.accept(payload);
            }
        });
        add(chip);
    }
}