package com.harmoni.pos.aiorder.service;

import com.harmoni.pos.aiorder.client.CustomerClient;
import com.harmoni.pos.aiorder.client.CustomerClient.CustomerApiException;
import com.harmoni.pos.aiorder.dto.CreateCustomerRequest;
import com.harmoni.pos.aiorder.dto.CustomerResponse;
import com.vaadin.flow.server.VaadinSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * HTTP-backed implementation of {@link CustomerService}.
 * <p>
 * Delegates persistence to the customer backend and keeps the resolved
 * customer fresh in the {@link VaadinSession} as attribute {@code customerId}
 * so the ordering service can open a customer session from it.
 *
 * @author Husain Harmoni
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerClientService implements CustomerService {

    private final CustomerClient customerClient;

    /**
     * Returns whether a customer has been resolved for the given Vaadin session.
     *
     * @param sessionId the Vaadin session id
     * @return {@code true} if a customer is already linked to this session
     */
    @Override
    public boolean exists(String sessionId) {
        return getCustomerResponse(sessionId).isPresent();
    }

    /**
     * Returns the customer linked to the given Vaadin session, if any.
     * <p>
     * The customer id is stored in the {@link VaadinSession} attribute
     * {@code customerId}. If present, this method fetches the customer
     * from the backend and returns it.
     *
     * @param sessionId the Vaadin session id
     * @return the customer, or {@link Optional#empty()} when not yet resolved
     */
    @Override
    public Optional<CustomerResponse> getCustomerResponse(String sessionId) {
        VaadinSession vs = VaadinSession.getCurrent();
        if (vs == null) return Optional.empty();
        Object storedCustomerId = vs.getAttribute("customerId");
        if (!(storedCustomerId instanceof Long customerId)) return Optional.empty();
        try {
            CustomerResponse cr = customerClient.getById(customerId);
            return Optional.ofNullable(cr);
        } catch (CustomerApiException e) {
            log.warn("Customer {} lookup failed: {}", customerId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Looks up a customer by exact phone number.
     *
     * @param phone the phone number to search for
     * @return the first matching customer, or {@link Optional#empty()} when none found
     */
    @Override
    public Optional<CustomerResponse> findByPhone(String phone) {
        if (phone == null || phone.isBlank()) return Optional.empty();
        String target = normalizePhone(phone);
        // The backend search is a free-text match, so it can also return someone whose name
        // or email contains the digits. Keep only an exact phone match; otherwise we would
        // sign the customer in as whoever happened to be returned first.
        List<CustomerResponse> customers = customerClient.searchByPhone(phone);
        return customers.stream()
                .filter(c -> c.phone() != null && normalizePhone(c.phone()).equals(target))
                .findFirst();
    }

    /**
     * Reduces a phone number to its digits so that formatting differences (spaces, dashes,
     * parentheses) do not prevent a match.
     *
     * @param phone the phone number to normalize
     * @return the digits of the number
     */
    private static String normalizePhone(String phone) {
        return phone.replaceAll("[^0-9]", "");
    }

    /**
     * Logs an existing customer in or registers a new one, then binds the
     * resulting customer to the current Vaadin session.
     *
     * @param sessionId the Vaadin session id
     * @param request   the customer details to register if unknown
     * @return the resolved customer
     */
    @Override
    public CustomerResponse loginOrRegister(String sessionId, CreateCustomerRequest request) {
        CustomerResponse customer = findByPhone(request.phone())
                .orElseGet(() -> {
                    log.info("Registering new customer '{}'", request.name());
                    return customerClient.create(request);
                });
        VaadinSession vs = VaadinSession.getCurrent();
        if (vs != null) {
            vs.setAttribute("customerId", customer.id());
            // Force a fresh backend session for this customer; a cached one may belong to a
            // previously signed-in customer in the same Vaadin session.
            vs.setAttribute("customerSessionId", null);
        }
        log.info("Customer {} resolved via phone {}", customer.id(), request.phone());
        return customer;
    }
}