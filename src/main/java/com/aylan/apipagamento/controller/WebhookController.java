package com.aylan.apipagamento.controller;

import com.aylan.apipagamento.service.PurchaseService;
import com.aylan.apipagamento.service.WebhookSignatureValidator;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Recebe e processa notificações de pagamento do Mercado Pago.
 *
 * <p>Fluxo de segurança:
 * <ol>
 *   <li>Valida a assinatura HMAC-SHA256 via {@link WebhookSignatureValidator}.</li>
 *   <li>Filtra apenas notificações do tipo {@code payment}.</li>
 *   <li>Delega o processamento ao {@link PurchaseService}.</li>
 * </ol>
 *
 * <p>Retorna sempre HTTP 200 ao MP (mesmo em erros internos), para evitar
 * reenvios desnecessários de eventos que não podemos processar de forma diferente.
 */
@Slf4j
@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
@Tag(name = "Webhooks", description = "Recebimento de notificações do Mercado Pago")
public class WebhookController {

    private final PurchaseService purchaseService;
    private final WebhookSignatureValidator signatureValidator;

    @PostMapping("/mercadopago")
    @Operation(summary = "Receber notificação de pagamento", description = "Endpoint chamado pelo Mercado Pago via webhook")
    public ResponseEntity<String> receiveNotification(
            @RequestHeader(value = "x-signature",  required = false) String xSignature,
            @RequestHeader(value = "x-request-id", required = false) String xRequestId,
            @RequestBody JsonNode body) {

        String type   = body.path("type").asText("");
        String dataId = body.path("data").path("id").asText(null);

        // ── Filtra notificações que não são do tipo payment ────────────────────
        if (!"payment".equals(type)) {
            log.debug("Notificação webhook ignorada. type={}", type);
            return ResponseEntity.ok("Notificação ignorada");
        }

        // ── Valida presença do data.id ────────────────────────────────────────
        if (dataId == null || dataId.isBlank()) {
            log.warn("Notificação webhook do tipo payment recebida sem data.id");
            return ResponseEntity.badRequest().body("data.id ausente");
        }

        // ── Valida assinatura HMAC ────────────────────────────────────────────
        if (!signatureValidator.isValid(xSignature, xRequestId, dataId)) {
            log.warn("Notificação webhook rejeitada por assinatura inválida. dataId={}", dataId);
            return ResponseEntity.status(401).body("Assinatura inválida");
        }

        // ── Processa o pagamento ──────────────────────────────────────────────
        try {
            Long paymentId = Long.parseLong(dataId);
            purchaseService.processPaymentNotification(paymentId);
            return ResponseEntity.ok("Notificação processada");
        } catch (NumberFormatException e) {
            log.error("data.id não é um Long válido: {}", dataId);
            return ResponseEntity.badRequest().body("data.id inválido");
        } catch (Exception e) {
            // Retorna 200 ao MP para não receber reenvios de eventos que não podemos processar
            log.error("Erro ao processar notificação webhook. dataId={}", dataId, e);
            return ResponseEntity.ok("Erro interno ao processar notificação");
        }
    }
}
