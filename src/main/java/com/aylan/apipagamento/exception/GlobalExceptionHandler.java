package com.aylan.apipagamento.exception;

import com.aylan.apipagamento.dto.response.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tratamento centralizado de exceções da API.
 *
 * <p><strong>Princípio de segurança:</strong> nenhuma mensagem de exceção interna
 * ({@code e.getMessage()}) é exposta ao cliente. Detalhes são logados internamente
 * e o cliente recebe apenas mensagens genéricas e seguras.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Erros de validação do Bean Validation (@Valid).
     * Retorna mapa field → mensagem para facilitar o feedback no frontend.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("Dados inválidos na requisição", fieldErrors));
    }

    /**
     * Parâmetros de path/query com tipo errado (ex: /purchases/abc em vez de /purchases/1).
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.debug("Tipo de argumento inválido. param={} value={}", ex.getName(), ex.getValue());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("Parâmetro inválido: " + ex.getName()));
    }

    /**
     * Exceções de negócio não tratadas especificamente.
     * Log detalhado internamente; cliente recebe mensagem genérica.
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntime(RuntimeException ex) {
        log.error("Exceção não tratada", ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Erro interno. Se o problema persistir, entre em contato com o suporte."));
    }

    /**
     * Fallback para qualquer exceção checked não capturada acima.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
        log.error("Erro inesperado", ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Ocorreu um erro inesperado. Tente novamente."));
    }
}
