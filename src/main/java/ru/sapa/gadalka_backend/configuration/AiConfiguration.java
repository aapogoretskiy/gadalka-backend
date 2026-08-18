package ru.sapa.gadalka_backend.configuration;

import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@Configuration
public class AiConfiguration {

    /**
     * Таймаут ожидания ответа на ОДИН вызов LLM.
     * <p>
     * Важно, чтобы значение было меньше клиентского таймаута фронта (90с) — тогда
     * пользователь получает осмысленную ошибку от нас, а не обрыв соединения.
     */
    private static final Duration AI_RESPONSE_TIMEOUT = Duration.ofSeconds(60);

    /** Таймаут установки TCP-соединения — отдельно от ожидания ответа. */
    private static final int AI_CONNECT_TIMEOUT_MS = 10_000;

    /**
     * Размер пула для параллельных вызовов AI.
     * <p>
     * Было 16. После перевода карточных интерпретаций на параллельное выполнение
     * один расклад «Кельтский крест» занимает до 11 потоков сразу, и на 16 потоках уже три одновременных пользователя выстраивались бы в очередь — то есть выигрыш
     * от параллельности съедался бы ожиданием свободного потока.
     * <p>
     * 48 — компромисс: потоки здесь почти всё время просто ждут ответа по сети
     * (блокирующий {@code .block()}), процессор не потребляют, а верхняя граница
     * защищает нас же от того, чтобы завалить API провайдера при всплеске нагрузки.
     */
    private static final int AI_POOL_SIZE = 48;

    @Bean
    public WebClient openRouterWebClient(@Value("${openrouter.url}") String url,
                                         @Value("${openrouter.api-key}") String apiKey) {
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(aiHttpClient()))
                .baseUrl(url)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON_VALUE)
                .build();
    }

    @Bean
    public WebClient aiTunnelWebClient(@Value("${aitunnel.url}") String url,
                                       @Value("${aitunnel.api-key}") String apiKey) {
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(aiHttpClient()))
                .baseUrl(url)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * HTTP-клиент с таймаутами для всех обращений к LLM-провайдерам.
     * Создаётся отдельным экземпляром на каждый WebClient — так у провайдеров
     * не будет общего пула соединений и проблемы одного не влияют на другой.
     */
    private HttpClient aiHttpClient() {
        return HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, AI_CONNECT_TIMEOUT_MS)
                .responseTimeout(AI_RESPONSE_TIMEOUT);
    }

    /**
     * Пул для параллельных вызовов AI (общая интерпретация расклада, интерпретации
     * отдельных карт, LLM pre-check чувствительности — см. FortuneService/DreamService
     * и OpenAiCompatibleInterpretationService#interpret). Отдельный bounded-пул,
     * а не common ForkJoinPool — вызовы внутри блокирующие ({@code WebClient...block()}),
     * нельзя занимать ими общий пул, которым пользуются parallelStream и другие части
     * приложения.
     * <p>
     * Потоки именованные (ai-pool-N) — по имени в логах сразу видно, что поток занят
     * ожиданием ответа модели, а не чем-то ещё.
     */
    @Bean
    public Executor aiTaskExecutor() {
        AtomicInteger counter = new AtomicInteger(1);
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "ai-pool-" + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
        return Executors.newFixedThreadPool(AI_POOL_SIZE, threadFactory);
    }
}
