package com.harmoni.pos.aiorder.service;

import com.harmoni.pos.aiorder.dto.CreateCustomerRequest;
import com.harmoni.pos.aiorder.dto.CustomerResponse;

import java.util.Optional;

/**
 * Resolves the current Vaadin user to a registered customer.
 * <p>
 * Implementations back onto the customer backend over HTTP; the resolved
 * customer id is stored in the {@link com.vaadin.flow.server.VaadinSession}.
 */
public interface CustomerService {

    /**
     * Returns whether a customer has been resolved for the given Vaadin session.
     *
     * @param sessionId the Vaadin session id
     * @return {@code true} if a customer is already linked to this session
     */
    boolean exists(String sessionId);

    /**
     * Returns the customer linked to the given Vaadin session, if any.
     *
     * @param sessionId the Vaadin session id
     * @return the customer, or {@link Optional#empty()} when not yet resolved
     */
    Optional<CustomerResponse> getCustomerResponse(String sessionId);

    /**
     * Looks up a customer by exact phone number.
     *
     * @param phone the phone number to search for
     * @return the first matching customer, or {@link Optional#empty()} when none found
     */
    Optional<CustomerResponse> findByPhone(String phone);

    /**
     * Logs an existing customer in or registers a new one, then binds the
     * resulting customer to the current Vaadin session.
     *
     * @param sessionId the Vaadin session id
     * @param request   the customer details to register if unknown
     * @return the resolved customer
     */
    CustomerResponse loginOrRegister(String sessionId, CreateCustomerRequest request);
}