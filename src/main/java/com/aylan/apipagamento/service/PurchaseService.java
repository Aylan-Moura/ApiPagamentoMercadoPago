package com.aylan.apipagamento.service;

import com.aylan.apipagamento.dto.request.CheckoutRequest;
import com.aylan.apipagamento.dto.response.CheckoutResponse;
import com.aylan.apipagamento.dto.response.PurchaseStatusResponse;
import com.aylan.apipagamento.model.Purchase;
import com.aylan.apipagamento.model.PurchaseStatus;
import com.aylan.apipagamento.repository.PurchaseRepository;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.payment.Payment;
import com.mercadopago.resources.preference.Preference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Orquestra o fluxo de compra: criação de preferência e atualização via webhook.
 *
 * <p>É a única camada que conhece tanto o repositório quanto o serviço do MP,
 * garantindo que os controllers permaneçam como simples adaptadores HTTP.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseService {

    private final PurchaseRepository purchaseRepository;
    private final MercadoPagoService mercadoPagoService;

    /**
     * Cria uma compra pendente e uma preferência no Mercado Pago.
     *
     * @param request dados do comprador e do produto
     * @return URL de checkout para redirecionar o cliente
     * @throws MPException    erro de comunicação com o SDK
     * @throws MPApiException erro retornado pela API do Mercado Pago
     */
    @Transactional
    public CheckoutResponse createCheckout(CheckoutRequest request) throws MPException, MPApiException {
        // 1. Persiste a compra em PENDING para ter o ID antes de chamar o MP
        Purchase purchase = new Purchase();
        purchase.setEmail(request.getEmail());
        purchase.setProductId(request.getProductId());
        purchase.setStatus(PurchaseStatus.PENDING);
        purchase = purchaseRepository.save(purchase);

        // 2. Cria a preferência no Mercado Pago usando o ID da Purchase como referência
        Preference preference = mercadoPagoService.criarPreferencia(
                request.getEmail(),
                request.getProductId(),
                purchase.getId().toString()
        );

        // 3. Atualiza a compra com o preferenceId retornado
        purchase.setPreferenceId(preference.getId());
        purchaseRepository.save(purchase);

        log.info("Checkout criado. purchaseId={} preferenceId={}", purchase.getId(), preference.getId());
        return new CheckoutResponse(preference.getInitPoint());
    }

    /**
     * Retorna o status público de uma compra, sem expor dados pessoais.
     *
     * @param id ID interno da compra
     * @return Optional com o status, ou vazio se não encontrado
     */
    @Transactional(readOnly = true)
    public Optional<PurchaseStatusResponse> getPurchaseStatus(Long id) {
        return purchaseRepository.findById(id)
                .map(p -> new PurchaseStatusResponse(p.getId(), p.getStatus(), p.getPreferenceId()));
    }

    /**
     * Processa a notificação de pagamento recebida via webhook do Mercado Pago.
     * Busca o pagamento no MP e atualiza o status da compra correspondente.
     *
     * @param paymentId ID do pagamento vindo do webhook
     */
    @Transactional
    public void processPaymentNotification(Long paymentId) {
        try {
            Payment payment = mercadoPagoService.buscarPagamento(paymentId);
            String externalReference = payment.getExternalReference();

            if (externalReference == null || externalReference.isBlank()) {
                log.warn("Pagamento MP id={} sem externalReference — ignorado", paymentId);
                return;
            }

            Long purchaseId = Long.parseLong(externalReference);
            purchaseRepository.findById(purchaseId).ifPresentOrElse(
                    purchase -> {
                        PurchaseStatus newStatus = mapMpStatus(payment.getStatus());
                        purchase.setPaymentId(String.valueOf(payment.getId()));
                        purchase.setStatus(newStatus);
                        purchaseRepository.save(purchase);
                        log.info("Purchase id={} atualizada para status={}", purchaseId, newStatus);
                    },
                    () -> log.warn("Purchase id={} referenciada pelo pagamento MP id={} não encontrada",
                                   purchaseId, paymentId)
            );

        } catch (NumberFormatException e) {
            log.error("externalReference do pagamento MP id={} não é um Long válido", paymentId, e);
        } catch (MPException | MPApiException e) {
            log.error("Falha ao buscar pagamento MP id={}", paymentId, e);
            throw new RuntimeException("Falha ao consultar pagamento no Mercado Pago", e);
        }
    }

    private PurchaseStatus mapMpStatus(String mpStatus) {
        if (mpStatus == null) return PurchaseStatus.PENDING;
        return switch (mpStatus) {
            case "approved"  -> PurchaseStatus.APPROVED;
            case "rejected"  -> PurchaseStatus.REJECTED;
            case "cancelled" -> PurchaseStatus.CANCELLED;
            default          -> PurchaseStatus.PENDING;
        };
    }
}
