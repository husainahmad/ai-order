package com.harmoni.pos.aiorder.ui.component;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.server.VaadinSession;

/**
 * Top app bar displayed at the top of the order view.
 * <p>
 * Contains the store name on the left and a logout button on the right. Styled with a
 * sticky position and backdrop blur for a frosted-glass effect.
 *
 * @author Husain Harmoni
 */
public class Header extends HorizontalLayout {

    private final Span storeName;
    private final Button logoutButton;

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

        String name = storeName == null || storeName.isBlank() ? "Kopi Harmoni" : storeName;
        this.storeName = new Span(name);
        this.storeName.addClassName("store-name");

        HorizontalLayout left = new HorizontalLayout(this.storeName);
        left.setDefaultVerticalComponentAlignment(Alignment.CENTER);
        left.addClassName("header-left");

        logoutButton = new Button("Keluar", new Icon(VaadinIcon.SIGN_OUT));
        logoutButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        logoutButton.addClassName("logout-button");
        logoutButton.setAriaLabel("Keluar");
        logoutButton.addClickListener(event -> logout());

        add(left, logoutButton);
        setFlexGrow(1, left);
    }

    /**
     * Ends the customer's session and returns to the landing page.
     * <p>
     * Clearing the stored customer is what makes the app treat the visitor as signed out;
     * the session id is dropped too so the next visit starts a fresh anonymous session rather
     * than reattaching to the previous customer's chat.
     */
    private void logout() {
        VaadinSession session = VaadinSession.getCurrent();
        if (session != null) {
            session.setAttribute("customerId", null);
            session.setAttribute("customerResponse", null);
            session.setAttribute("customerSessionId", null);
            session.setAttribute("sessionId", null);
        }
        UI ui = UI.getCurrent();
        if (ui != null) {
            ui.navigate("");
        }
    }
}
