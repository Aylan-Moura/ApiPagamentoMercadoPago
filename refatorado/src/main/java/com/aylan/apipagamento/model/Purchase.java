package com.aylan.apipagamento.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Representa uma tentativa de compra e seu ciclo de vida no Mercado Pago.
 *
 * <p>O fluxo típico é:
 * <ol>
 *   <li>Criada com status {@link PurchaseStatus#PENDING} no checkout.</li>
 *   <li>Atualizada para {@link PurchaseStatus#APPROVED}, {@link PurchaseStatus#REJECTED}
 *       ou {@link PurchaseStatus#CANCELLED} via webhook.</li>
 * </ol>
 */
@Entity
@Table(
    name = "purchases",
    indexes = {
        @Index(name = "idx_purchases_preference_id", columnList = "preference_id"),
        @Index(name = "idx_purchases_payment_id",    columnList = "payment_id"),
        @Index(name = "idx_purchases_status",        columnList = "status")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class Purchase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** E-mail do comprador — dado pessoal (LGPD), nunca expor em APIs públicas. */
    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "product_id", nullable = false, length = 255)
    private String productId;

    /** ID da preferência criada na API do Mercado Pago. */
    @Column(name = "preference_id", length = 255)
    private String preferenceId;

    /** ID do pagamento, preenchido pelo webhook após confirmação. */
    @Column(name = "payment_id", length = 255)
    private String paymentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private PurchaseStatus status = PurchaseStatus.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
