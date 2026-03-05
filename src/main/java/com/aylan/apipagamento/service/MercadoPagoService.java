package com.aylan.apipagamento.service;

import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.preference.*;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.payment.Payment;
import com.mercadopago.resources.preference.Preference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.List;

/**
 * Fachada para a API do Mercado Pago.
 *
 * <p>Responsabilidades:
 * <ul>
 *   <li>Configurar o SDK na inicialização (fail-fast se token ausente).</li>
 *   <li>Criar preferências de pagamento.</li>
 *   <li>Consultar pagamentos por ID.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MercadoPagoService {

    private static final String WEBHOOK_PATH = "/api/webhooks/mercadopago";

    // ── Injeção via construtor pelo @RequiredArgsConstructor ──────────────────
    private final PaymentClient paymentClient;
    private final PreferenceClient preferenceClient;

    @Value("${mercadopago.access-token}")
    private String accessToken;

    @Value("${mercadopago.app-base-url:}")
    private String appBaseUrl;

    @Value("${app.frontend.url:}")
    private String frontendUrl;

    @Value("${app.product.title:Curso Completo de Backend}")
    private String productTitle;

    @Value("${app.product.description:Aprenda a criar APIs profissionais}")
    private String productDescription;

    /**
     * Preço do produto injetado diretamente como {@link BigDecimal}.
     * O Spring converte automaticamente; qualquer valor malformado causa falha
     * na inicialização do contexto, não em tempo de execução.
     */
    @Value("${app.product.price:99.90}")
    private BigDecimal productPrice;

    /**
     * Configura o SDK do Mercado Pago e valida as dependências obrigatórias.
     * Falha na inicialização da aplicação (fail-fast) se o access token estiver ausente,
     * evitando que o serviço suba em estado inválido e falhe apenas no primeiro request.
     */
    @PostConstruct
    public void init() {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException(
                "MERCADOPAGO_ACCESS_TOKEN não está configurado. " +
                "Defina a variável de ambiente antes de iniciar a aplicação."
            );
        }
        MercadoPagoConfig.setAccessToken(accessToken);
        log.info("SDK Mercado Pago inicializado com sucesso.");

        if (appBaseUrl == null || appBaseUrl.isBlank()) {
            log.warn("MERCADOPAGO_APP_BASE_URL não configurado — webhooks desativados neste ambiente.");
        }
    }

    /**
     * Cria uma preferência de pagamento no Mercado Pago.
     *
     * @param email             e-mail do comprador
     * @param productId         ID do produto
     * @param externalReference referência interna (ID da Purchase) para reconciliação
     * @return preferência criada com initPoint para redirect
     */
    public Preference criarPreferencia(String email, String productId, String externalReference)
            throws MPException, MPApiException {

        log.info("Criando preferência MP para purchase externalReference={}", externalReference);

        PreferenceItemRequest item = PreferenceItemRequest.builder()
                .id(productId)
                .title(productTitle)
                .description(productDescription)
                .quantity(1)
                .currencyId("BRL")
                .unitPrice(productPrice)
                .build();

        String base = resolveBaseUrl();
        PreferenceBackUrlsRequest backUrls = PreferenceBackUrlsRequest.builder()
                .success(base + "/success")
                .pending(base + "/pending")
                .failure(base + "/failure")
                .build();

        PreferenceRequest.PreferenceRequestBuilder requestBuilder = PreferenceRequest.builder()
                .items(List.of(item))
                .payer(PreferencePayerRequest.builder().email(email).build())
                .backUrls(backUrls)
                .autoReturn("approved")
                .externalReference(externalReference);

        if (appBaseUrl != null && !appBaseUrl.isBlank()) {
            String notificationUrl = appBaseUrl.replaceAll("/$", "") + WEBHOOK_PATH;
            requestBuilder.notificationUrl(notificationUrl);
            log.info("Webhook configurado em: {}", notificationUrl);
        }

        Preference preference = preferenceClient.create(requestBuilder.build());
        log.info("Preferência MP criada. id={} externalReference={}", preference.getId(), externalReference);
        return preference;
    }

    /**
     * Busca um pagamento pelo ID no Mercado Pago.
     *
     * @param paymentId ID do pagamento retornado pelo webhook
     * @return objeto Payment com status e referência externa
     */
    public Payment buscarPagamento(Long paymentId) throws MPException, MPApiException {
        log.debug("Buscando pagamento MP id={}", paymentId);
        return paymentClient.get(paymentId);
    }

    private String resolveBaseUrl() {
        if (frontendUrl != null && !frontendUrl.isBlank()) {
            return frontendUrl.replaceAll("/$", "");
        }
        return "https://landing-pag-api-pagamentos.vercel.app";
    }
}
