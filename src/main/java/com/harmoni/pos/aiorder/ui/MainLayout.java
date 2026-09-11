package com.harmoni.pos.aiorder.ui;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.html.Div;

/**
 * Shared application shell used by all views via {@code layout = MainLayout.class}.
 * <p>
 * Renders an empty navbar and content area — each view provides its own
 * complete UI composition (header, content, input) without relying on
 * the AppLayout chrome.
 *
 * @author Husain Harmoni
 */
public class MainLayout extends AppLayout {

    /** Creates the shell with an empty navbar and an empty content area. */
    public MainLayout() {
        addToNavbar(new Div());
        setContent(new Div());
    }
}