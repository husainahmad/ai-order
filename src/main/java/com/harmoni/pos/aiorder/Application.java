package com.harmoni.pos.aiorder;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.server.PWA;
import com.vaadin.flow.theme.Theme;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Application entry point and Vaadin app shell configuration.
 * <p>
 * Binds the {@code app} theme and enables server push for real-time
 * message streaming to the chat view.
 *
 * @author Husain Harmoni
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@Theme("app")
@Push
@PWA(
        name = "Kopi Harmoni Ordering",
        shortName = "Kopi Harmoni",
        backgroundColor = "#fafaf9",
        themeColor = "#3d6b4f"
)
public class Application implements AppShellConfigurator {

    /**
     * Boots the Spring Boot application.
     *
     * @param args command-line arguments passed through to Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
