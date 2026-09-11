package com.harmoni.pos.aiorder.client;

import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * Builds the reactive {@link WebClient} used to call the customer backend.
 * <p>
 * Applies the configured base URL, connect/response timeouts, and a larger
 * in-memory buffer so large menu payloads are not truncated.
 *
 * @author Husain Harmoni
 */
@Configuration
public class WebClientConfig {

    /**
     * Creates the shared customer-backend WebClient bean.
     *
     * @param properties the customer API configuration (base URL and timeout)
     * @return a configured WebClient for customer API calls
     */
    @Bean
    public WebClient customerWebClient(CustomerApiProperties properties) {

        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, properties.timeoutMs())
                .responseTimeout(Duration.ofMillis(properties.timeoutMs()));

        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();

        return WebClient.builder()
                .baseUrl(properties.baseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(strategies)
                .build();
    }
}