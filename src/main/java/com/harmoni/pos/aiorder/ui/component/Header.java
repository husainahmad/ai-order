package com.harmoni.pos.aiorder.ui.component;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

/**
 * Top app bar displayed at the top of the order view.
 * <p>
 * Contains a back navigation button on the left and the store name.
 * Styled with a sticky position and backdrop blur for a frosted-glass effect.
 *
 * @author Husain Harmoni
 */
public class Header extends HorizontalLayout {

    private final Button backButton;
    private final Span storeName;

    /**
     * Creates the header bar with the given store name.
     *
     * @param storeName the store name to display; falls back to "Kopi Harmoni" if null or blank
     */
    public Header(String storeName) {
        addClassName("header");
        setWidthFull();
        setDefaultVerticalComponentAlignment(Alignment.CENTER);
        setSpacing(false);

        backButton = new Button(new Icon(VaadinIcon.ARROW_LEFT));
        backButton.addThemeVariants(ButtonVariant.LUMO_CONTRAST);
        backButton.addClassName("back-button");
        backButton.setAriaLabel("Kembali");

        String name = storeName == null || storeName.isBlank() ? "Kopi Harmoni" : storeName;
        this.storeName = new Span(name);
        this.storeName.addClassName("store-name");

        HorizontalLayout left = new HorizontalLayout(this.storeName);
        left.setDefaultVerticalComponentAlignment(Alignment.CENTER);
        left.addClassName("header-left");

        add(backButton, left);
        setFlexGrow(1, left);
    }
}