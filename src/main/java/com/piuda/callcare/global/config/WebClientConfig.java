package com.piuda.callcare.global.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebClientConfig {

    private static final int HIRA_MAX_IN_MEMORY_SIZE = 10 * 1024 * 1024; // 10MB

    private HttpClient defaultHttpClient() {
        return HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5_000)
                .responseTimeout(Duration.ofSeconds(30))
                .doOnConnected(conn ->
                        conn.addHandlerLast(new ReadTimeoutHandler(30, TimeUnit.SECONDS)));
    }

    @Primary
    @Bean
    public WebClient webClient() {
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(defaultHttpClient()))
                .build();
    }

    @Bean
    @Qualifier("hiraWebClient")
    public WebClient hiraWebClient() {
        ExchangeStrategies exchangeStrategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(HIRA_MAX_IN_MEMORY_SIZE))
                .build();

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(defaultHttpClient()))
                .exchangeStrategies(exchangeStrategies)
                .build();
    }
}
