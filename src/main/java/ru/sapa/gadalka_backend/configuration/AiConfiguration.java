package ru.sapa.gadalka_backend.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@Configuration
public class AiConfiguration {

    @Bean
    public WebClient openRouterWebClient(@Value("${openrouter.url}") String url,
                                         @Value("${openrouter.api-key}") String apiKey) {
        return WebClient.builder()
                .baseUrl(url)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON_VALUE)
                .build();
    }

    @Bean
    public WebClient aiTunnelWebClient(@Value("${aitunnel.url}") String url,
                                       @Value("${aitunnel.api-key}") String apiKey) {
        return WebClient.builder()
                .baseUrl(url)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * Пул для параллельных вызовов AI (генерация интерпретации + LLM pre-check
     * чувствительности одновременно, см. FortuneService/DreamService). Отдельный
     * bounded-пул, а не common ForkJoinPool — вызовы внутри блокирующие
     * ({@code WebClient...block()}), нельзя занимать ими общий пул, которым
     * пользуются parallelStream и другие части приложения.
     */
    @Bean
    public Executor aiTaskExecutor() {
        return Executors.newFixedThreadPool(16);
    }
}
