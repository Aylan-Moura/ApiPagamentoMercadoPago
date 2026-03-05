package com.aylan.apipagamento.controller;

import com.aylan.apipagamento.dto.response.CheckoutResponse;
import com.aylan.apipagamento.dto.response.PurchaseStatusResponse;
import com.aylan.apipagamento.model.PurchaseStatus;
import com.aylan.apipagamento.service.PurchaseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mercadopago.exceptions.MPApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CheckoutController.class)
@DisplayName("CheckoutController")
class CheckoutControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private PurchaseService purchaseService;

    // ── POST /api/checkout ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/checkout")
    class PostCheckout {

        @Test
        @DisplayName("deve retornar 200 com checkoutUrl quando dados são válidos")
        void deveRetornar200QuandoDadosValidos() throws Exception {
            when(purchaseService.createCheckout(any()))
                    .thenReturn(new CheckoutResponse("https://mercadopago.com.br/checkout/xxx"));

            mockMvc.perform(post("/api/checkout")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "email": "comprador@email.com",
                                    "productId": "CURSO-01"
                                }
                            """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.checkoutUrl").value("https://mercadopago.com.br/checkout/xxx"));
        }

        @Test
        @DisplayName("deve retornar 400 quando email está ausente")
        void deveRetornar400SemEmail() throws Exception {
            mockMvc.perform(post("/api/checkout")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "productId": "CURSO-01"
                                }
                            """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.email").exists());
        }

        @Test
        @DisplayName("deve retornar 400 quando email é inválido")
        void deveRetornar400EmailInvalido() throws Exception {
            mockMvc.perform(post("/api/checkout")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "email": "nao-e-um-email",
                                    "productId": "CURSO-01"
                                }
                            """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.email").exists());
        }

        @Test
        @DisplayName("deve retornar 400 quando productId está ausente")
        void deveRetornar400SemProductId() throws Exception {
            mockMvc.perform(post("/api/checkout")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "email": "comprador@email.com"
                                }
                            """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.productId").exists());
        }

        @Test
        @DisplayName("deve retornar 502 quando o Mercado Pago falha")
        void deveRetornar502QuandoMPFalha() throws Exception {
            when(purchaseService.createCheckout(any())).thenThrow(MPApiException.class);

            mockMvc.perform(post("/api/checkout")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "email": "comprador@email.com",
                                    "productId": "CURSO-01"
                                }
                            """))
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.message").exists());
        }

        @Test
        @DisplayName("resposta de erro não deve conter detalhes internos da exceção")
        void respostaDeErrNaoDeveConterDetalhesInternos() throws Exception {
            when(purchaseService.createCheckout(any()))
                    .thenThrow(new RuntimeException("senha-do-banco-secreta"));

            mockMvc.perform(post("/api/checkout")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "email": "comprador@email.com",
                                    "productId": "CURSO-01"
                                }
                            """))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.message").value(
                            org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("senha-do-banco-secreta"))
                    ));
        }
    }

    // ── GET /api/purchases/{id} ────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/purchases/{id}")
    class GetPurchaseStatus {

        @Test
        @DisplayName("deve retornar 200 com status quando compra existe")
        void deveRetornar200QuandoExiste() throws Exception {
            PurchaseStatusResponse response = new PurchaseStatusResponse(42L, PurchaseStatus.APPROVED, "pref-123");
            when(purchaseService.getPurchaseStatus(42L)).thenReturn(Optional.of(response));

            mockMvc.perform(get("/api/purchases/42"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(42))
                    .andExpect(jsonPath("$.status").value("APPROVED"))
                    .andExpect(jsonPath("$.preferenceId").value("pref-123"));
        }

        @Test
        @DisplayName("deve retornar 404 quando compra não existe")
        void deveRetornar404QuandoNaoExiste() throws Exception {
            when(purchaseService.getPurchaseStatus(99L)).thenReturn(Optional.empty());

            mockMvc.perform(get("/api/purchases/99"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("deve retornar 400 para ID inválido (não numérico)")
        void deveRetornar400ParaIdInvalido() throws Exception {
            mockMvc.perform(get("/api/purchases/abc"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("resposta não deve conter campo email (proteção PII/LGPD)")
        void respostaNaoDeveConterEmail() throws Exception {
            PurchaseStatusResponse response = new PurchaseStatusResponse(1L, PurchaseStatus.PENDING, "pref-xyz");
            when(purchaseService.getPurchaseStatus(1L)).thenReturn(Optional.of(response));

            mockMvc.perform(get("/api/purchases/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").doesNotExist());
        }
    }

    // ── Endpoints de retorno ───────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/return/*")
    class ReturnEndpoints {

        @Test
        @DisplayName("deve redirecionar para frontend/success")
        void deveRedirecionarSuccess() throws Exception {
            mockMvc.perform(get("/api/return/success"))
                    .andExpect(status().isFound())
                    .andExpect(header().string("Location",
                            org.hamcrest.Matchers.containsString("/success")));
        }

        @Test
        @DisplayName("deve redirecionar para frontend/pending")
        void deveRedirecionarPending() throws Exception {
            mockMvc.perform(get("/api/return/pending"))
                    .andExpect(status().isFound())
                    .andExpect(header().string("Location",
                            org.hamcrest.Matchers.containsString("/pending")));
        }

        @Test
        @DisplayName("deve redirecionar para frontend/failure")
        void deveRedirecionarFailure() throws Exception {
            mockMvc.perform(get("/api/return/failure"))
                    .andExpect(status().isFound())
                    .andExpect(header().string("Location",
                            org.hamcrest.Matchers.containsString("/failure")));
        }
    }
}
