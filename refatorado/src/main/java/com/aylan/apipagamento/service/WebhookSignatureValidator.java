package com.aylan.apipagamento.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Valida o header {@code x-signature} das notificações webhook do Mercado Pago
 * usando HMAC-SHA256 conforme a documentação oficial.
 *
 * <p><strong>Comportamento de segurança:</strong> se {@code MERCADOPAGO_WEBHOOK_SECRET}
 * não estiver configurado, {@link #isValid} sempre retorna {@code false}.
 * Nunca abra mão da validação em nenhum ambiente — um webhook não validado
 * permite que atacantes manipulem o status de pagamentos.
 *
 * @see <a href="https://www.mercadopago.com.br/developers/pt/docs/your-integrations/notifications/webhooks">
 *      Documentação Webhooks MP</a>
 */
@Slf4j
@Component
public class WebhookSignatureValidator {

    private static final String HMAC_SHA256 = "HmacSHA256";

    @Value("${mercadopago.webhook-secret:}")
    private String webhookSecret;

    /**
     * Verifica se a notificação foi enviada genuinamente pelo Mercado Pago.
     *
     * @param xSignature valor do header {@code x-signature} (formato: {@code ts=...,v1=...})
     * @param xRequestId valor do header {@code x-request-id}
     * @param dataId     ID do evento extraído do corpo da requisição
     * @return {@code true} somente se a assinatura for criptograficamente válida
     */
    public boolean isValid(String xSignature, String xRequestId, String dataId) {
        // ── Fail-secure: sem secret configurado, rejeita SEMPRE ──────────────
        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.error("MERCADOPAGO_WEBHOOK_SECRET não está configurado. " +
                      "Todos os webhooks serão rejeitados por segurança. " +
                      "Configure a variável de ambiente para habilitar o processamento.");
            return false;
        }

        if (xSignature == null || xSignature.isBlank()) {
            log.warn("Webhook recebido sem header x-signature — rejeitado");
            return false;
        }
        if (xRequestId == null || xRequestId.isBlank()) {
            log.warn("Webhook recebido sem header x-request-id — rejeitado");
            return false;
        }
        if (dataId == null || dataId.isBlank()) {
            log.warn("Webhook recebido sem data.id — rejeitado");
            return false;
        }

        String ts = null;
        String v1 = null;
        for (String part : xSignature.split(",")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2) {
                String k = kv[0].trim();
                String v = kv[1].trim();
                if ("ts".equals(k))      ts = v;
                else if ("v1".equals(k)) v1 = v;
            }
        }

        if (ts == null || v1 == null) {
            log.warn("x-signature malformado (ts ou v1 ausentes) — rejeitado");
            return false;
        }

        // Manifesto conforme documentação do Mercado Pago
        String manifest = "id:" + dataId.toLowerCase() + ";request-id:" + xRequestId + ";ts:" + ts + ";";

        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] hash = mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8));
            String computed = HexFormat.of().formatHex(hash);

            boolean valid = computed.equalsIgnoreCase(v1);
            if (!valid) {
                log.warn("Assinatura HMAC do webhook não confere — requisição rejeitada");
            }
            return valid;

        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Falha crítica ao validar assinatura do webhook", e);
            return false;
        }
    }
}
