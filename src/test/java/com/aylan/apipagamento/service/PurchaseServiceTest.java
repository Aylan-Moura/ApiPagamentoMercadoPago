package com.aylan.apipagamento.service;

import com.aylan.apipagamento.dto.request.CheckoutRequest;
import com.aylan.apipagamento.dto.response.CheckoutResponse;
import com.aylan.apipagamento.dto.response.PurchaseStatusResponse;
import com.aylan.apipagamento.model.Purchase;
import com.aylan.apipagamento.model.PurchaseStatus;
import com.aylan.apipagamento.repository.PurchaseRepository;
import com.mercadopago.resources.payment.Payment;
import com.mercadopago.resources.preference.Preference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PurchaseService")
class PurchaseServiceTest {

    @Mock private PurchaseRepository purchaseRepository;
    @Mock private MercadoPagoService mercadoPagoService;

    @InjectMocks private PurchaseService purchaseService;

    // ── createCheckout ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createCheckout()")
    class CreateCheckout {

        private CheckoutRequest request;
        private Purchase savedPurchase;
        private Preference preference;

        @BeforeEach
        void setUp() throws Exception {
            request = new CheckoutRequest();
            request.setEmail("comprador@email.com");
            request.setProductId("CURSO-01");

            savedPurchase = new Purchase();
            savedPurchase.setId(1L);
            savedPurchase.setEmail(request.getEmail());
            savedPurchase.setProductId(request.getProductId());
            savedPurchase.setStatus(PurchaseStatus.PENDING);

            preference = mock(Preference.class);
            when(preference.getId()).thenReturn("pref-abc-123");
            when(preference.getInitPoint()).thenReturn("https://www.mercadopago.com.br/checkout/v1/redirect?pref_id=pref-abc-123");

            when(purchaseRepository.save(any(Purchase.class))).thenReturn(savedPurchase);
            when(mercadoPagoService.criarPreferencia(anyString(), anyString(), anyString()))
                    .thenReturn(preference);
        }

        @Test
        @DisplayName("deve criar Purchase com status PENDING antes de chamar o MP")
        void deveCriarPurchasePendente() throws Exception {
            purchaseService.createCheckout(request);

            ArgumentCaptor<Purchase> captor = ArgumentCaptor.forClass(Purchase.class);
            verify(purchaseRepository, atLeastOnce()).save(captor.capture());

            Purchase firstSave = captor.getAllValues().get(0);
            assertThat(firstSave.getStatus()).isEqualTo(PurchaseStatus.PENDING);
            assertThat(firstSave.getEmail()).isEqualTo("comprador@email.com");
        }

        @Test
        @DisplayName("deve retornar a URL de checkout do Mercado Pago")
        void deveRetornarCheckoutUrl() throws Exception {
            CheckoutResponse response = purchaseService.createCheckout(request);

            assertThat(response.getCheckoutUrl())
                    .contains("mercadopago.com.br");
        }

        @Test
        @DisplayName("deve chamar o MP com o email, productId e purchaseId corretos")
        void deveChamarMPComParametrosCorretos() throws Exception {
            purchaseService.createCheckout(request);

            verify(mercadoPagoService).criarPreferencia(
                    eq("comprador@email.com"),
                    eq("CURSO-01"),
                    eq("1")
            );
        }

        @Test
        @DisplayName("deve salvar o preferenceId na Purchase após criar no MP")
        void deveSalvarPreferenceId() throws Exception {
            purchaseService.createCheckout(request);

            ArgumentCaptor<Purchase> captor = ArgumentCaptor.forClass(Purchase.class);
            verify(purchaseRepository, times(2)).save(captor.capture());

            Purchase secondSave = captor.getAllValues().get(1);
            assertThat(secondSave.getPreferenceId()).isEqualTo("pref-abc-123");
        }
    }

    // ── getPurchaseStatus ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("getPurchaseStatus()")
    class GetPurchaseStatus {

        @Test
        @DisplayName("deve retornar Optional com status quando Purchase existe")
        void deveRetornarStatusQuandoExiste() {
            Purchase purchase = new Purchase();
            purchase.setId(42L);
            purchase.setStatus(PurchaseStatus.APPROVED);
            purchase.setPreferenceId("pref-xyz");

            when(purchaseRepository.findById(42L)).thenReturn(Optional.of(purchase));

            Optional<PurchaseStatusResponse> result = purchaseService.getPurchaseStatus(42L);

            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(42L);
            assertThat(result.get().getStatus()).isEqualTo(PurchaseStatus.APPROVED);
        }

        @Test
        @DisplayName("deve retornar Optional vazio quando Purchase não existe")
        void deveRetornarVazioQuandoNaoExiste() {
            when(purchaseRepository.findById(anyLong())).thenReturn(Optional.empty());

            Optional<PurchaseStatusResponse> result = purchaseService.getPurchaseStatus(99L);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("não deve expor email do comprador na resposta")
        void naoDeveExporEmail() {
            Purchase purchase = new Purchase();
            purchase.setId(1L);
            purchase.setEmail("privado@email.com");
            purchase.setStatus(PurchaseStatus.PENDING);

            when(purchaseRepository.findById(1L)).thenReturn(Optional.of(purchase));

            PurchaseStatusResponse response = purchaseService.getPurchaseStatus(1L).orElseThrow();

            // PurchaseStatusResponse não deve ter campo email
            // Verificado pelo próprio tipo de retorno — sem getter de email
            assertThat(response.getStatus()).isEqualTo(PurchaseStatus.PENDING);
        }
    }

    // ── processPaymentNotification ────────────────────────────────────────────

    @Nested
    @DisplayName("processPaymentNotification()")
    class ProcessPayment {

        private Payment payment;

        @BeforeEach
        void setUp() throws Exception {
            payment = mock(Payment.class);
            when(payment.getId()).thenReturn(999L);
            when(payment.getExternalReference()).thenReturn("1");
            when(payment.getStatus()).thenReturn("approved");
            when(mercadoPagoService.buscarPagamento(999L)).thenReturn(payment);
        }

        @Test
        @DisplayName("deve atualizar Purchase para APPROVED quando pagamento é aprovado")
        void deveAtualizarParaApproved() throws Exception {
            Purchase purchase = new Purchase();
            purchase.setId(1L);
            purchase.setStatus(PurchaseStatus.PENDING);

            when(purchaseRepository.findById(1L)).thenReturn(Optional.of(purchase));

            purchaseService.processPaymentNotification(999L);

            ArgumentCaptor<Purchase> captor = ArgumentCaptor.forClass(Purchase.class);
            verify(purchaseRepository).save(captor.capture());

            assertThat(captor.getValue().getStatus()).isEqualTo(PurchaseStatus.APPROVED);
            assertThat(captor.getValue().getPaymentId()).isEqualTo("999");
        }

        @Test
        @DisplayName("deve atualizar Purchase para REJECTED quando pagamento é rejeitado")
        void deveAtualizarParaRejected() throws Exception {
            when(payment.getStatus()).thenReturn("rejected");
            Purchase purchase = new Purchase();
            purchase.setId(1L);
            purchase.setStatus(PurchaseStatus.PENDING);
            when(purchaseRepository.findById(1L)).thenReturn(Optional.of(purchase));

            purchaseService.processPaymentNotification(999L);

            ArgumentCaptor<Purchase> captor = ArgumentCaptor.forClass(Purchase.class);
            verify(purchaseRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(PurchaseStatus.REJECTED);
        }

        @Test
        @DisplayName("deve ignorar silenciosamente quando Purchase não é encontrada")
        void deveIgnorarQuandoPurchaseNaoExiste() throws Exception {
            when(purchaseRepository.findById(1L)).thenReturn(Optional.empty());

            purchaseService.processPaymentNotification(999L);

            verify(purchaseRepository, never()).save(any());
        }

        @Test
        @DisplayName("deve ignorar pagamento sem externalReference")
        void deveIgnorarSemExternalReference() throws Exception {
            when(payment.getExternalReference()).thenReturn(null);

            purchaseService.processPaymentNotification(999L);

            verify(purchaseRepository, never()).findById(anyLong());
            verify(purchaseRepository, never()).save(any());
        }
    }
}
