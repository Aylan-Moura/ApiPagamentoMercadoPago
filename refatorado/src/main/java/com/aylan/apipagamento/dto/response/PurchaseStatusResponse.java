package com.aylan.apipagamento.dto.response;

import com.aylan.apipagamento.model.PurchaseStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Status de uma compra exposto publicamente.
 *
 * <p><strong>Atenção PII/LGPD:</strong> o e-mail do comprador foi intencionalmente
 * omitido desta resposta. O endpoint é público (sem autenticação) e IDs numéricos
 * sequenciais são facilmente enumeráveis (OWASP A01 – IDOR). Expor o e-mail aqui
 * resultaria em vazamento de dados pessoais de todos os clientes.
 */
@Getter
@AllArgsConstructor
@Schema(description = "Status atual de uma compra")
public class PurchaseStatusResponse {

    @Schema(description = "ID interno da compra", example = "42")
    private final Long id;

    @Schema(description = "Status atual do pagamento", example = "APPROVED")
    private final PurchaseStatus status;

    @Schema(description = "ID da preferência no Mercado Pago", example = "1234567890-abc-def")
    private final String preferenceId;
}
