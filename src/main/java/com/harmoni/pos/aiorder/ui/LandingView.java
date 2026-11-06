package com.harmoni.pos.aiorder.ui;

import com.harmoni.pos.aiorder.ui.component.CustomerGateCard;
import com.harmoni.pos.aiorder.dto.CustomerResponse;
import com.harmoni.pos.aiorder.service.CustomerService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

import java.util.UUID;

/**
 * Entry point at {@code /} — the landing page for the Kopi Harmoni ordering app.
 * <p>
 * On first visit, resolves or generates a {@code sessionId} and checks whether the
 * customer is already registered via {@link CustomerService#exists(String)}.
 * If registered, immediately forwards to {@code /order?sessionId=xxx}.
 * Otherwise, shows a {@link CustomerGateCard} in place of the page content.
 * <p>
 * The sign-in card is the page, not a layer over it: there is nothing here to go back to,
 * and a modal would put a dismissable surface over an empty screen. Shows a centered
 * loading spinner while resolving, and a recoverable error state with a retry button if the
 * customer service is unavailable.
 *
 * @author Husain Harmoni
 */
@Route(value = "", layout = MainLayout.class)
@RequiredArgsConstructor
public class LandingView extends VerticalLayout implements BeforeEnterObserver {

    private static final Logger log = LoggerFactory.getLogger(LandingView.class);

    private final CustomerService customerService;
    private final Validator validator;
    private final Environment environment;

    private String sessionId;
    private VerticalLayout loadingState;
    private VerticalLayout errorState;
    private CustomerGateCard gateCard;

    {
        addClassName("landing-view");
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        loadingState = new VerticalLayout();
        loadingState.addClassName("landing-loading");
        loadingState.setAlignItems(Alignment.CENTER);
        loadingState.setSpacing(false);

        Div spinner = new Div();
        spinner.addClassName("landing-spinner");

        Span loadingText = new Span("Memuat...");
        loadingText.addClassName("landing-loading-text");

        loadingState.add(spinner, loadingText);
        add(loadingState);
    }

    /**
     * Pre-navigation hook that resolves the session and decides whether to forward
     * to the order view or show the sign-in card.
     * <p>
     * Session ID resolution order: URL parameter &gt; VaadinSession attribute &gt; new UUID.
     * If the customer service is unreachable, displays an error state instead.
     *
     * @param event the before-enter event carrying navigation context
     */
    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        String paramSid = event.getLocation().getQueryParameters().getParameters()
                .getOrDefault("sessionId", java.util.List.of()).stream().findFirst().orElse(null);
        if (paramSid != null && !paramSid.isBlank()) {
            sessionId = paramSid;
        } else {
            sessionId = (String) VaadinSession.getCurrent().getAttribute("sessionId");
        }
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString();
        }
        VaadinSession.getCurrent().setAttribute("sessionId", sessionId);

        boolean exists;
        try {
            exists = customerService.exists(sessionId);
        } catch (Exception e) {
            log.warn("Customer service unavailable sessionId={}: {}", sessionId, e.getMessage());
            showGateError("Layanan tidak tersedia saat ini. Silakan coba lagi.");
            return;
        }
        if (exists) {
            event.forwardTo("order?sessionId=" + sessionId);
            return;
        }

        showSignIn();
    }

    /**
     * Puts the sign-in card on the page in place of the loading spinner.
     * <p>
     * Rebuilt each time rather than reused, so a retry starts from an empty phone field
     * instead of whatever a previous failed attempt left behind.
     */
    private void showSignIn() {
        clearError();
        removeAll();
        gateCard = new CustomerGateCard(
                sessionId,
                environment.getProperty("app.store-name", "Kopi Harmoni"),
                customerService,
                validator,
                this::onCustomerRegistered);
        add(gateCard);
        gateCard.focusFirstField();
    }

    /**
     * Called after successful customer registration/login. Stores customer data
     * in the Vaadin session and navigates to the order view.
     *
     * @param customer the resolved customer response from the customer service
     */
    private void onCustomerRegistered(CustomerResponse customer) {
        VaadinSession.getCurrent().setAttribute("customerId", customer.id());
        VaadinSession.getCurrent().setAttribute("customerResponse", customer);
        VaadinSession.getCurrent().setAttribute("sessionId", sessionId);

        UI ui = UI.getCurrent();
        if (ui != null) {
            ui.navigate("order");
        }
    }

    /**
     * Replaces the current view content with a centered error message and a retry button.
     *
     * @param text the error message to display
     */
    private void showGateError(String text) {
        clearError();
        removeAll();

        errorState = new VerticalLayout();
        errorState.addClassName("landing-error");
        errorState.setAlignItems(Alignment.CENTER);
        errorState.setSpacing(false);

        Span message = new Span(text);
        message.addClassName("landing-error-text");

        Button retry = new Button("Coba Lagi", e -> showSignIn());
        retry.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        errorState.add(message, retry);
        add(errorState);
    }

    /** Removes the error state from the view if present. */
    private void clearError() {
        if (errorState != null && errorState.getParent().isPresent()) {
            remove(errorState);
        }
        errorState = null;
    }
}