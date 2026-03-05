package com.aylan.apipagamento.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Dados necessários para iniciar um checkout")
public class CheckoutRequest {

    @NotBlank(message = "O ID do produto é obrigatório")
    @Size(max = 255, message = "O ID do produto deve ter no máximo 255 caracteres")
    @Schema(description = "Identificador único do produto", example = "CURSO-BACKEND-01")
    private String productId;

    @NotBlank(message = "O e-mail é obrigatório")
    @Email(message = "Formato de e-mail inválido")
    @Size(max = 255, message = "O e-mail deve ter no máximo 255 caracteres")
    @Schema(description = "E-mail do comprador", example = "comprador@email.com")
    private String email;
}
