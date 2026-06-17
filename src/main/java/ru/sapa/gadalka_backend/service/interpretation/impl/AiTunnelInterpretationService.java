package ru.sapa.gadalka_backend.service.interpretation.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service("aitunnel")
@RequiredArgsConstructor
public class AiTunnelInterpretationService extends OpenAiCompatibleInterpretationService {

    @Qualifier("aiTunnelWebClient")
    private final WebClient aiTunnelWebClient;

    @Value("${aitunnel.model}")
    private String aiModel;

    @Override
    protected WebClient getClient() {
        return aiTunnelWebClient;
    }

    @Override
    protected String getModel() {
        return aiModel;
    }

    @Override
    public String getProvider() {
        return "aitunnel";
    }
}
