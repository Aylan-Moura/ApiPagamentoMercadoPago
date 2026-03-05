package com.aylan.apipagamento.model;

/**
 * Estados possíveis de uma compra ao longo do ciclo de pagamento.
 */
public enum PurchaseStatus {
    /** Preferência criada, aguardando ação do comprador. */
    PENDING,
    /** Pagamento confirmado pelo Mercado Pago via webhook. */
    APPROVED,
    /** Pagamento recusado (saldo insuficiente, dados inválidos etc.). */
    REJECTED,
    /** Compra cancelada pelo comprador ou expirada. */
    CANCELLED
}
