package com.aylan.apipagamento.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Schema(description = "Resposta do checkout com a URL de pagamento")
public class CheckoutResponse {

    @Schema(description = "URL do checkout do Mercado Pago para redirecionar o comprador",
            example = "https://www.mercadopago.com.br/checkout/v1/redirect?pref_id=...")
    private final String checkoutUrl;
}
