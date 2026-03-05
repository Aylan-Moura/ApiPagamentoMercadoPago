package com.aylan.apipagamento.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;

/**
 * Configuração de CORS da aplicação.
 *
 * <p>As origens permitidas são definidas via variável de ambiente
 * {@code APP_CORS_ORIGINS}, separadas por vírgula. Em produção,
 * somente o domínio do frontend deve ser listado.
 */
@Slf4j
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.cors.allowed-origins:}")
    private String allowedOriginsConfig;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        List<String> origins = Arrays.stream(allowedOriginsConfig.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        if (origins.isEmpty()) {
            log.warn("APP_CORS_ORIGINS não configurado. CORS restrito a localhost para segurança.");
            origins = List.of("http://localhost:3000", "http://localhost:63342");
        }

        log.info("CORS configurado para origens: {}", origins);

        registry.addMapping("/api/**")
                .allowedOrigins(origins.toArray(new String[0]))
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("Content-Type", "Accept", "Authorization")
                .allowCredentials(true)
                .maxAge(3600);

        // Webhook: aberto para o Mercado Pago (sem restrição de origem)
        registry.addMapping("/api/webhooks/**")
                .allowedOriginPatterns("*")
                .allowedMethods("POST", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false);
    }
}
