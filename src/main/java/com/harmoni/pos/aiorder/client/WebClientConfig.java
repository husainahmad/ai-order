package com.harmoni.pos.aiorder.client;

import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

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

    /**
     * Creates a WebClient specific for cart operations.
     * <p>
     * Cart calls may have different timeout requirements or need to hit a different
     * backend URL (e.g., separated service). Currently reuses the same base URL.
     *
     * @param cartProperties the cart API configuration
     * @return a WebClient configured for cart API calls
     */
    @Bean
    public WebClient cartWebClient(CartApiProperties cartProperties) {

        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, cartProperties.getTimeoutMs())
                .responseTimeout(Duration.ofMillis(cartProperties.getTimeoutMs()));

        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();

        return WebClient.builder()
                .baseUrl(cartProperties.getUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(strategies)
                .build();
    }
}
