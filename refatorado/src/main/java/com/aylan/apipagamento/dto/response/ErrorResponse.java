package com.aylan.apipagamento.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.time.Instant;
import java.util.Map;

/**
 * Estrutura padronizada de resposta de erro para todas as APIs.
 * Nunca inclui detalhes internos de exceção — apenas mensagens seguras para o cliente.
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Resposta de erro padronizada")
public class ErrorResponse {

    @Schema(description = "Timestamp do erro em UTC", example = "2026-02-26T14:30:00Z")
    private final Instant timestamp = Instant.now();

    @Schema(description = "Mensagem de erro legível para o usuário", example = "Dados inválidos na requisição")
    private final String message;

    @Schema(description = "Mapa de campos com erros de validação (presente apenas em erros 400)")
    private final Map<String, String> fieldErrors;

    public ErrorResponse(String message) {
        this.message = message;
        this.fieldErrors = null;
    }

    public ErrorResponse(String message, Map<String, String> fieldErrors) {
        this.message = message;
        this.fieldErrors = fieldErrors;
    }
}
