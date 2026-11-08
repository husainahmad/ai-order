package com.harmoni.pos.aiorder.ui.component;

import com.harmoni.pos.aiorder.dto.CreateCustomerRequest;
import com.harmoni.pos.aiorder.dto.CustomerResponse;
import com.harmoni.pos.aiorder.client.CustomerClient;
import com.harmoni.pos.aiorder.service.CustomerService;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Sign-in card shown on the landing page before a customer can place an order.
 * <p>
 * This is an ordinary element, not a modal. The sign-in <em>is</em> the landing page: there
 * is nothing behind it to go back to, and a dialog would put a dismissable layer over an
 * empty screen — with the awkward side effect that closing it has to mean "failed".
 * <p>
 * The flow is two steps, because the two cases are different questions. A returning customer
 * only has to say which number is theirs; a new one has to say who they are. Asking everybody
 * for a name up front made the common case look like the uncommon one.
 * <ol>
 *   <li><strong>Phone</strong> — one field. Submitting looks the number up.</li>
 *   <li><strong>Known number</strong> — signs them straight in.</li>
 *   <li><strong>Unknown number</strong> — the card grows into name, email and phone, with
 *       the phone carried over read-only so nothing is typed twice.</li>
 * </ol>
 * A "Daftar" link on the first step opens the registration form directly for someone who
 * knows they are new, without making them look their own number up first.
 * <p>
 * Registration and lookup both end in {@link CustomerService#loginOrRegister}, so the
 * session binding is unchanged.
 *
 * @author Husain Harmoni
 */
public class CustomerGateCard extends Div {

    private static final String PHONE_PATTERN = "^[0-9+\\-()\\s]{5,30}$";

    private final String sessionId;
    private final CustomerService customerService;
    private final Validator validator;
    private final Consumer<CustomerResponse> onSuccess;

    private final VerticalLayout body;
    private final Span subtitle;
    private final Span stepLabel;

    private final TextField phoneField;
    private final TextField nameField;
    private final EmailField emailField;

    private final Button primaryButton;
    private final Button secondaryButton;

    /** Which step is on screen. Drives what the primary button does. */
    private Step step = Step.PHONE;

    private enum Step {
        /** Asking for the number. */
        PHONE,
        /** The number is unknown; collecting the rest to register. */
        REGISTER
    }

    /**
     * Creates the sign-in card.
     *
     * @param sessionId       the Vaadin session ID to associate the customer with
     * @param storeName       the store name shown in the greeting header
     * @param customerService the service for customer lookup and registration
     * @param validator       Jakarta Bean Validation validator for field-level constraint checks
     * @param onSuccess       callback invoked with the resolved customer after successful login/register
     */
    public CustomerGateCard(
            String sessionId,
            String storeName,
            CustomerService customerService,
            Validator validator,
            Consumer<CustomerResponse> onSuccess
    ) {
        this.sessionId = sessionId;
        this.customerService = customerService;
        this.validator = validator;
        this.onSuccess = onSuccess;

        addClassName("customer-gate-card");
        setWidthFull();

        Icon logo = new Icon(VaadinIcon.COFFEE);
        logo.addClassName("gate-logo");

        Span title = new Span(storeName != null && !storeName.isBlank() ? storeName : "Kopi Harmoni");
        title.addClassName("gate-title");

        stepLabel = new Span();
        stepLabel.addClassName("gate-step");

        subtitle = new Span();
        subtitle.addClassName("gate-subtitle");

        VerticalLayout hero = new VerticalLayout(logo, title, subtitle, stepLabel);
        hero.addClassName("gate-hero");
        hero.setAlignItems(FlexComponent.Alignment.CENTER);
        hero.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
        hero.setPadding(false);
        hero.setSpacing(false);

        phoneField = buildPhoneField();
        nameField = buildNameField();
        emailField = buildEmailField();

        body = new VerticalLayout(phoneField);
        body.addClassName("gate-body");
        body.setPadding(false);
        body.setSpacing(false);
        body.setWidthFull();

        primaryButton = new Button("Lanjutkan", e -> primaryAction());
        primaryButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        primaryButton.setWidthFull();
        primaryButton.addClassName("gate-submit");
        primaryButton.addClickShortcut(Key.ENTER);

        secondaryButton = new Button("Belum punya akun? Daftar", e -> toggleAuthMode());
        secondaryButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        secondaryButton.addClassName("gate-secondary");

        VerticalLayout actions = new VerticalLayout(primaryButton, secondaryButton);
        actions.setPadding(false);
        actions.setSpacing(false);
        actions.setWidthFull();
        actions.addClassName("gate-actions");

        add(hero, body, actions);

        showPhoneStep();
    }

    /**
     * Puts the cursor in the phone field.
     * <p>
     * Separate from the constructor so the caller can wait until the card is attached: a
     * field cannot take focus before it is on the page.
     */
    public void focusFirstField() {
        phoneField.focus();
    }

    // ------------------------------------------------------------------ fields

    private TextField buildPhoneField() {
        TextField field = new TextField("No. HP");
        field.setPlaceholder("0812 3456 7890");
        field.setRequiredIndicatorVisible(true);
        field.setMaxLength(30);
        field.setWidthFull();
        field.setHelperText("Dipakai untuk mengenali Anda saat memesan lagi");
        field.setClearButtonVisible(true);
        field.setValueChangeMode(ValueChangeMode.EAGER);
        field.addValueChangeListener(e -> {
            String value = e.getValue();
            boolean malformed = value != null && !value.isBlank() && !value.matches(PHONE_PATTERN);
            field.setInvalid(malformed);
            if (malformed) {
                field.setErrorMessage("Format nomor tidak valid");
            }
            refreshButtonState();
        });
        return field;
    }

    private TextField buildNameField() {
        TextField field = new TextField("Nama");
        field.setPlaceholder("Nama Anda");
        field.setRequiredIndicatorVisible(true);
        field.setMaxLength(100);
        field.setWidthFull();
        field.setValueChangeMode(ValueChangeMode.EAGER);
        field.addValueChangeListener(e -> refreshButtonState());
        return field;
    }

    private EmailField buildEmailField() {
        EmailField field = new EmailField("Email (opsional)");
        field.setPlaceholder("nama@email.com");
        field.setMaxLength(150);
        field.setWidthFull();
        field.setClearButtonVisible(true);
        field.setValueChangeMode(ValueChangeMode.EAGER);
        field.setHelperText("Untuk struk digital");
        return field;
    }

    // ------------------------------------------------------------------ steps

    /**
     * Renders the first step: the phone number alone, with a link to register instead.
     */
    private void showPhoneStep() {
        step = Step.PHONE;
        stepLabel.setText("");
        stepLabel.setVisible(false);
        subtitle.setText("Masuk dengan nomor HP untuk mulai memesan.");
        body.removeAll();
        // The field goes back to editable: the lookup path sets it read-only.
        phoneField.setReadOnly(false);
        phoneField.setInvalid(false);
        body.add(phoneField);
        primaryButton.setText("Lanjutkan");
        secondaryButton.setText("Belum punya akun? Daftar");
        secondaryButton.setVisible(true);
        refreshButtonState();
        phoneField.focus();
    }

    /**
     * Renders the registration step.
     * <p>
     * Reached two ways: after a lookup finds the number unknown, or directly from the
     * "Daftar" link. In the first case the phone is carried over read-only, since it is what
     * the account is keyed on; registering directly leaves it editable so the customer can
     * type it here.
     *
     * @param phone the number already entered on the first step, or {@code null} for a
     *              direct registration
     */
    private void showRegisterStep(String phone) {
        boolean fromLookup = phone != null;
        step = Step.REGISTER;
        stepLabel.setVisible(fromLookup);
        stepLabel.setText(fromLookup ? "Langkah 2 dari 2" : "");
        subtitle.setText(fromLookup
                ? "Nomor ini belum terdaftar. Lengkapi nama Anda untuk membuat akun."
                : "Daftar akun baru untuk mulai memesan.");
        body.removeAll();
        phoneField.setReadOnly(fromLookup);
        if (fromLookup) {
            phoneField.setValue(phone);
        }
        phoneField.setInvalid(false);
        if (fromLookup) {
            body.add(nameField, emailField, phoneField);
        } else {
            body.add(nameField, phoneField, emailField);
        }
        primaryButton.setText("Daftar & Mulai");
        secondaryButton.setText(fromLookup ? "Ganti nomor" : "Sudah punya akun? Masuk");
        secondaryButton.setVisible(true);
        refreshButtonState();
        nameField.focus();
    }

    /**
     * Switches between signing in and registering: the secondary button's meaning depends on
     * the step currently on screen.
     */
    private void toggleAuthMode() {
        if (step == Step.PHONE) {
            showRegisterStep(null);
        } else {
            showPhoneStep();
        }
    }

    /**
     * Runs whichever step the card is on.
     */
    private void primaryAction() {
        if (step == Step.PHONE) {
            lookupPhone();
        } else {
            register();
        }
    }

    /**
     * Looks the entered number up.
     * <p>
     * A known number signs the customer in immediately — there is nothing left to ask. An
     * unknown one moves to the registration fields rather than failing, because "not found"
     * is an ordinary outcome here, not an error.
     */
    private void lookupPhone() {
        String phone = trimOrNull(phoneField.getValue());
        if (phone == null) {
            phoneField.setInvalid(true);
            phoneField.setErrorMessage("No. HP wajib diisi");
            return;
        }
        if (!phone.matches(PHONE_PATTERN)) {
            phoneField.setInvalid(true);
            phoneField.setErrorMessage("Format nomor tidak valid");
            return;
        }
        phoneField.setInvalid(false);

        setBusy(true);
        Optional<CustomerResponse> existing;
        try {
            existing = customerService.findByPhone(phone);
        } catch (Exception e) {
            // A lookup failure is not the same as "not registered": telling the customer to
            // register again would risk a duplicate account. Ask them to retry instead.
            setBusy(false);
            notifyError("Tidak bisa memeriksa nomor ini. Coba lagi.");
            return;
        }
        setBusy(false);

        if (existing.isPresent()) {
            signIn(existing.get());
        } else {
            // Carried onto the next step, where it is shown read-only.
            showRegisterStep(phone);
        }
    }

    /**
     * Registers the unknown number and signs the customer in.
     */
    private void register() {
        String phone = trimOrNull(phoneField.getValue());
        String name = trimOrNull(nameField.getValue());
        String email = trimOrNull(emailField.getValue());

        boolean invalid = false;
        if (name == null) {
            nameField.setInvalid(true);
            nameField.setErrorMessage("Nama wajib diisi");
            invalid = true;
        } else if (name.length() > 100) {
            nameField.setInvalid(true);
            nameField.setErrorMessage("Maksimal 100 karakter");
            invalid = true;
        } else {
            nameField.setInvalid(false);
        }
        if (phone == null) {
            phoneField.setInvalid(true);
            phoneField.setErrorMessage("No. HP wajib diisi");
            invalid = true;
        } else if (!phone.matches(PHONE_PATTERN)) {
            phoneField.setInvalid(true);
            phoneField.setErrorMessage("Format nomor tidak valid");
            invalid = true;
        } else {
            phoneField.setInvalid(false);
        }
        if (email != null && email.length() > 150) {
            emailField.setInvalid(true);
            emailField.setErrorMessage("Maksimal 150 karakter");
            invalid = true;
        }
        if (invalid) {
            return;
        }

        // The same constraints the customer service enforces, checked here so a bad value is
        // reported next to its own field instead of as a failed request.
        CreateCustomerRequest request = new CreateCustomerRequest(name, phone, email);
        Set<ConstraintViolation<CreateCustomerRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            reportViolations(violations);
            return;
        }

        setBusy(true);
        try {
            signIn(customerService.loginOrRegister(sessionId, request));
        } catch (CustomerClient.CustomerApiException e) {
            setBusy(false);
            notifyError(e.getMessage());
        } catch (Exception e) {
            setBusy(false);
            notifyError("Gagal mendaftar: " + e.getMessage());
        }
    }

    /**
     * Shows a validation message on the field it came from.
     *
     * @param violations the failed constraints
     */
    private void reportViolations(Set<ConstraintViolation<CreateCustomerRequest>> violations) {
        for (ConstraintViolation<CreateCustomerRequest> violation : violations) {
            String message = violation.getMessage();
            switch (violation.getPropertyPath().toString()) {
                case "name" -> {
                    nameField.setInvalid(true);
                    nameField.setErrorMessage(message);
                }
                case "phone" -> {
                    phoneField.setInvalid(true);
                    phoneField.setErrorMessage(message);
                }
                case "email" -> {
                    emailField.setInvalid(true);
                    emailField.setErrorMessage(message);
                }
                default -> { }
            }
        }
    }

    /**
     * Hands the resolved customer to the caller.
     * <p>
     * The card does not remove itself or navigate: it does not own the page it sits on. The
     * caller decides what happens next, which is also what keeps it usable from anywhere.
     *
     * @param customer the resolved customer
     */
    private void signIn(CustomerResponse customer) {
        setBusy(false);
        if (onSuccess != null) {
            onSuccess.accept(customer);
        }
        Notification.show("Selamat datang, " + customer.name() + "!")
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    /**
     * Enables the primary button only when the current step has what it needs.
     */
    private void refreshButtonState() {
        boolean ready = switch (step) {
            case PHONE -> {
                String phone = trimOrNull(phoneField.getValue());
                yield phone != null && !phoneField.isInvalid();
            }
            case REGISTER -> {
                String name = trimOrNull(nameField.getValue());
                String phone = trimOrNull(phoneField.getValue());
                yield name != null && !nameField.isInvalid()
                        && phone != null && !phoneField.isInvalid();
            }
        };
        primaryButton.setEnabled(ready);
    }

    /**
     * Disables the form while a request is in flight.
     * <p>
     * The button keeps its own label rather than being renamed to "Checking...": a label that
     * changes under the cursor reads as a different button. The dimmed fields already say
     * that something is happening.
     *
     * @param busy whether a request is running
     */
    private void setBusy(boolean busy) {
        body.setEnabled(!busy);
        primaryButton.setEnabled(!busy);
        secondaryButton.setEnabled(!busy);
        if (busy) {
            addClassName("gate-busy");
        } else {
            removeClassName("gate-busy");
            refreshButtonState();
        }
    }

    private void notifyError(String message) {
        Notification notification = Notification.show(
                message == null || message.isBlank() ? "Terjadi kesalahan. Coba lagi." : message,
                5000, Notification.Position.MIDDLE);
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }

    /**
     * Trims the input string and returns {@code null} if the result is empty.
     *
     * @param value the input string
     * @return trimmed string, or {@code null} if blank
     */
    private static String trimOrNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
