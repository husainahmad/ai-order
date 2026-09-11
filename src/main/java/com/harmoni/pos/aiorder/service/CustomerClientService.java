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

    @Override
    public boolean exists(String sessionId) {
        return getCustomerResponse(sessionId).isPresent();
    }

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

    @Override
    public Optional<CustomerResponse> findByPhone(String phone) {
        if (phone == null || phone.isBlank()) return Optional.empty();
        List<CustomerResponse> customers = customerClient.searchByPhone(phone);
        return customers.stream().findFirst();
    }

    @Override
    public CustomerResponse loginOrRegister(String sessionId, CreateCustomerRequest request) {
        CustomerResponse customer = findByPhone(request.phone())
                .orElseGet(() -> {
                    log.info("Registering new customer '{}'", request.name());
                    return customerClient.create(request);
                });
        VaadinSession vs = VaadinSession.getCurrent();
        if (vs != null) vs.setAttribute("customerId", customer.id());
        log.info("Customer {} resolved via phone {}", customer.id(), request.phone());
        return customer;
    }
}