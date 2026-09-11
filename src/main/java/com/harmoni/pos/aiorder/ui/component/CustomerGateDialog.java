package com.harmoni.pos.aiorder.ui.component;

import com.harmoni.pos.aiorder.dto.CreateCustomerRequest;
import com.harmoni.pos.aiorder.dto.CustomerResponse;
import com.harmoni.pos.aiorder.client.CustomerClient;
import com.harmoni.pos.aiorder.service.CustomerService;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
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
import java.util.Set;
import java.util.function.Consumer;

/**
 * Modal login/registration dialog shown before the customer can place an order.
 * <p>
 * Uses the phone number as the login key:
 * <ul>
 *   <li><strong>Known phone</strong> — logs the customer in and reuses the stored name</li>
 *   <li><strong>Unknown phone</strong> — registers a new customer (name required)</li>
 * </ul>
 * Submits through {@link CustomerService#loginOrRegister(String, CreateCustomerRequest)}.
 * <p>
 * The submit button is enabled only when a valid phone number is entered.
 * Field-level validation messages are shown inline. On success, the dialog closes
 * and the {@code onSuccess} callback is invoked with the resolved customer.
 *
 * @author Husain Harmoni
 */
public class CustomerGateDialog extends Dialog {

    private final TextField nameField;
    private final TextField phoneField;
    private final EmailField emailField;
    private final Button submitButton;

    /**
     * Creates the customer gate dialog.
     *
     * @param sessionId       the Vaadin session ID to associate the customer with
     * @param storeName       the store name shown in the greeting header
     * @param customerService the service for customer lookup and registration
     * @param validator       Jakarta Bean Validation validator for field-level constraint checks
     * @param onSuccess       callback invoked with the resolved customer after successful login/register
     */
    public CustomerGateDialog(
            String sessionId,
            String storeName,
            CustomerService customerService,
            Validator validator,
            Consumer<CustomerResponse> onSuccess
    ) {
        setCloseOnEsc(false);
        setCloseOnOutsideClick(false);
        setDraggable(false);
        setResizable(false);
        setModal(true);
        setWidth("100%");
        setMaxWidth("400px");
        addClassName("customer-gate-dialog");

        Icon logo = new Icon(VaadinIcon.COFFEE);
        logo.addClassName("gate-logo");

        Span title = new Span(storeName != null && !storeName.isBlank() ? storeName : "Kopi Harmoni");
        title.addClassName("gate-title");

        Span subtitle = new Span("Masuk dengan No. HP untuk mulai memesan. Belum punya akun? Kamu akan otomatis terdaftar.");
        subtitle.addClassName("gate-subtitle");

        VerticalLayout hero = new VerticalLayout(logo, title, subtitle);
        hero.addClassName("gate-hero");
        hero.setAlignItems(FlexComponent.Alignment.CENTER);
        hero.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
        hero.setPadding(false);
        hero.setSpacing(true);

        phoneField = new TextField("No. HP");
        phoneField.setPlaceholder("0812xxxxxxx");
        phoneField.setRequiredIndicatorVisible(true);
        phoneField.setMaxLength(30);
        phoneField.setWidthFull();
        phoneField.setHelperText("Nomor ini dipakai untuk login di kunjungan berikutnya");
        phoneField.setClearButtonVisible(true);
        phoneField.setValueChangeMode(ValueChangeMode.EAGER);
        phoneField.setAutofocus(true);
        phoneField.addValueChangeListener(e -> {
            String val = e.getValue();
            if (val != null && !val.isBlank() && !val.matches("^[0-9+\\-()\\s]{5,30}$")) {
                phoneField.setInvalid(true);
                phoneField.setErrorMessage("Format HP tidak valid");
            } else {
                phoneField.setInvalid(false);
            }
        });

        nameField = new TextField("Nama");
        nameField.setPlaceholder("Budi (wajib jika daftar baru)");
        nameField.setMaxLength(100);
        nameField.setWidthFull();
        nameField.setValueChangeMode(ValueChangeMode.EAGER);
        nameField.setClearButtonVisible(true);
        nameField.setHelperText("Kosongkan jika sudah pernah daftar");

        emailField = new EmailField("Email (opsional)");
        emailField.setPlaceholder("budi@email.com");
        emailField.setMaxLength(150);
        emailField.setWidthFull();
        emailField.setClearButtonVisible(true);
        emailField.setValueChangeMode(ValueChangeMode.EAGER);

        submitButton = new Button("Masuk / Daftar", e -> handleSubmit(sessionId, customerService, validator, onSuccess));
        submitButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        submitButton.setWidthFull();
        submitButton.addClassName("gate-submit");
        submitButton.addClickShortcut(Key.ENTER);

        Runnable updateEnabled = () -> {
            boolean hasPhone = phoneField.getValue() != null && !phoneField.getValue().isBlank() && !phoneField.isInvalid();
            submitButton.setEnabled(hasPhone);
        };
        nameField.addValueChangeListener(e -> updateEnabled.run());
        phoneField.addValueChangeListener(e -> updateEnabled.run());
        updateEnabled.run();

        VerticalLayout content = new VerticalLayout(hero, phoneField, nameField, emailField);
        content.setPadding(false);
        content.setSpacing(true);
        content.setWidthFull();
        add(content);

        getFooter().add(submitButton);
    }

    /**
     * Handles form submission. Validates all fields, determines login vs. register
     * flow based on phone existence, performs Jakarta validation for new customers,
     * and calls the customer service. Shows error notifications on failure.
     *
     * @param sessionId       the current Vaadin session ID
     * @param customerService the customer service for login/register
     * @param validator       Jakarta validator for field constraints
     * @param onSuccess       callback invoked on success
     */
    private void handleSubmit(
            String sessionId,
            CustomerService customerService,
            Validator validator,
            Consumer<CustomerResponse> onSuccess
    ) {
        String name = trimOrNull(nameField.getValue());
        String phone = trimOrNull(phoneField.getValue());
        String email = trimOrNull(emailField.getValue());

        boolean hasError = false;
        if (phone == null || phone.isBlank()) {
            phoneField.setInvalid(true);
            phoneField.setErrorMessage("No. HP wajib diisi");
            hasError = true;
        } else if (!phone.matches("^[0-9+\\-()\\s]{5,30}$")) {
            phoneField.setInvalid(true);
            phoneField.setErrorMessage("Format HP tidak valid");
            hasError = true;
        } else {
            phoneField.setInvalid(false);
        }
        if (hasError) return;

        boolean isExisting = false;
        try {
            isExisting = customerService.findByPhone(phone).isPresent();
        } catch (Exception ignored) {}

        if (!isExisting) {
            if (name == null || name.isBlank()) {
                nameField.setInvalid(true);
                nameField.setErrorMessage("Nama wajib untuk pendaftaran baru");
                return;
            } else if (name.length() > 100) {
                nameField.setInvalid(true);
                nameField.setErrorMessage("Maksimal 100 karakter");
                return;
            } else {
                nameField.setInvalid(false);
            }
        } else {
            if (name != null && !name.isBlank() && name.length() > 100) {
                nameField.setInvalid(true);
                nameField.setErrorMessage("Maksimal 100 karakter");
                return;
            }
            nameField.setInvalid(false);
        }

        if (!isExisting) {
            CreateCustomerRequest requestForValidation = new CreateCustomerRequest(name, phone, email);
            Set<ConstraintViolation<CreateCustomerRequest>> violations = validator.validate(requestForValidation);
            if (!violations.isEmpty()) {
                for (ConstraintViolation<CreateCustomerRequest> v : violations) {
                    String prop = v.getPropertyPath().toString();
                    String msg = v.getMessage();
                    if ("name".equals(prop)) {
                        nameField.setInvalid(true);
                        nameField.setErrorMessage(msg);
                    } else if ("phone".equals(prop)) {
                        phoneField.setInvalid(true);
                        phoneField.setErrorMessage(msg);
                    } else if ("email".equals(prop)) {
                        emailField.setInvalid(true);
                        emailField.setErrorMessage(msg);
                    }
                }
                return;
            }
        } else if (email != null && !email.isBlank()) {
            CreateCustomerRequest req = new CreateCustomerRequest(name != null ? name : "Temp Name", phone, email);
            Set<ConstraintViolation<CreateCustomerRequest>> violations = validator.validate(req);
            for (ConstraintViolation<CreateCustomerRequest> v : violations) {
                if ("email".equals(v.getPropertyPath().toString())) {
                    emailField.setInvalid(true);
                    emailField.setErrorMessage(v.getMessage());
                    return;
                }
            }
            emailField.setInvalid(false);
        }

        CreateCustomerRequest request = new CreateCustomerRequest(name, phone, email);

        submitButton.setEnabled(false);
        submitButton.setText(isExisting ? "Masuk..." : "Mendaftar...");
        try {
            CustomerResponse response = customerService.loginOrRegister(sessionId, request);
            close();
            if (onSuccess != null) {
                onSuccess.accept(response);
            }
            String welcome = isExisting ? "Selamat datang kembali, " : "Selamat datang, ";
            Notification.show(welcome + response.name() + "!")
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        } catch (CustomerClient.CustomerApiException ex) {
            Notification n = Notification.show(ex.getMessage(), 5000, Notification.Position.MIDDLE);
            n.addThemeVariants(NotificationVariant.LUMO_ERROR);
            submitButton.setEnabled(true);
            submitButton.setText("Masuk / Daftar");
        } catch (IllegalArgumentException ex) {
            if (ex.getMessage() != null && ex.getMessage().contains("Nama diperlukan")) {
                nameField.setInvalid(true);
                nameField.setErrorMessage(ex.getMessage());
            } else {
                Notification n = Notification.show(ex.getMessage(), 5000, Notification.Position.MIDDLE);
                n.addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
            submitButton.setEnabled(true);
            submitButton.setText("Masuk / Daftar");
        } catch (Exception ex) {
            Notification n = Notification.show("Gagal: " + ex.getMessage(), 5000, Notification.Position.MIDDLE);
            n.addThemeVariants(NotificationVariant.LUMO_ERROR);
            submitButton.setEnabled(true);
            submitButton.setText("Masuk / Daftar");
        }
    }

    /**
     * Trims the input string and returns {@code null} if the result is empty.
     *
     * @param s the input string
     * @return trimmed string, or {@code null} if blank
     */
    private static String trimOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}