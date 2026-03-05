package com.aylan.apipagamento.controller;

import com.aylan.apipagamento.service.PurchaseService;
import com.aylan.apipagamento.service.WebhookSignatureValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WebhookController.class)
@DisplayName("WebhookController")
class WebhookControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private PurchaseService purchaseService;
    @MockitoBean private WebhookSignatureValidator signatureValidator;

    private static final String VALID_PAYMENT_BODY = """
        {
            "type": "payment",
            "data": { "id": "123456" }
        }
    """;

    // ── Notificação de pagamento válida ────────────────────────────────────────

    @Nested
    @DisplayName("Notificação válida do tipo payment")
    class NotificacaoValida {

        @Test
        @DisplayName("deve retornar 200 e processar o pagamento quando assinatura é válida")
        void deveProcessarERetornar200() throws Exception {
            when(signatureValidator.isValid(anyString(), anyString(), anyString())).thenReturn(true);

            mockMvc.perform(post("/api/webhooks/mercadopago")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("x-signature", "ts=123,v1=abc")
                            .header("x-request-id", "req-001")
                            .content(VALID_PAYMENT_BODY))
                    .andExpect(status().isOk());

            verify(purchaseService).processPaymentNotification(123456L);
        }
    }

    // ── Assinatura inválida ───────────────────────────────────────────────────

    @Nested
    @DisplayName("Assinatura inválida")
    class AssinaturaInvalida {

        @Test
        @DisplayName("deve retornar 401 quando assinatura é inválida")
        void deveRetornar401ParaAssinaturaInvalida() throws Exception {
            when(signatureValidator.isValid(anyString(), anyString(), anyString())).thenReturn(false);

            mockMvc.perform(post("/api/webhooks/mercadopago")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("x-signature", "ts=999,v1=invalido")
                            .header("x-request-id", "req-001")
                            .content(VALID_PAYMENT_BODY))
                    .andExpect(status().isUnauthorized());

            verify(purchaseService, never()).processPaymentNotification(anyLong());
        }
    }

    // ── Tipo de notificação diferente ─────────────────────────────────────────

    @Nested
    @DisplayName("Tipos de notificação não-payment")
    class TipoNaoPayment {

        @Test
        @DisplayName("deve ignorar notificação do tipo merchant_order")
        void deveIgnorarMerchantOrder() throws Exception {
            mockMvc.perform(post("/api/webhooks/mercadopago")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("x-signature", "ts=123,v1=abc")
                            .header("x-request-id", "req-001")
                            .content("""
                                {"type": "merchant_order", "data": {"id": "789"}}
                            """))
                    .andExpect(status().isOk());

            verify(purchaseService, never()).processPaymentNotification(anyLong());
            verify(signatureValidator, never()).isValid(any(), any(), any());
        }
    }

    // ── data.id ausente ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("Payload inválido")
    class PayloadInvalido {

        @Test
        @DisplayName("deve retornar 400 quando data.id está ausente")
        void deveRetornar400SemDataId() throws Exception {
            mockMvc.perform(post("/api/webhooks/mercadopago")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("x-signature", "ts=123,v1=abc")
                            .header("x-request-id", "req-001")
                            .content("""
                                {"type": "payment", "data": {}}
                            """))
                    .andExpect(status().isBadRequest());

            verify(purchaseService, never()).processPaymentNotification(anyLong());
        }
    }
}
