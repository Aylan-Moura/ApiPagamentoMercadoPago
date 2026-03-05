package com.aylan.apipagamento.controller;

import com.aylan.apipagamento.dto.request.CheckoutRequest;
import com.aylan.apipagamento.dto.response.CheckoutResponse;
import com.aylan.apipagamento.dto.response.ErrorResponse;
import com.aylan.apipagamento.dto.response.PurchaseStatusResponse;
import com.aylan.apipagamento.service.PurchaseService;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

/**
 * Endpoints de checkout e consulta de status de compra.
 *
 * <p>Este controller é um adapter HTTP puro: recebe requisições, delega ao
 * {@link PurchaseService} e devolve respostas. Nenhuma regra de negócio aqui.
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Checkout", description = "Criação de pagamentos e consulta de status")
public class CheckoutController {

    private final PurchaseService purchaseService;

    @Value("${app.frontend.url:https://landing-pag-api-pagamentos.vercel.app}")
    private String frontendUrl;

    // ── POST /api/checkout ─────────────────────────────────────────────────────

    @PostMapping("/checkout")
    @Operation(
        summary = "Iniciar checkout",
        description = "Cria uma compra pendente e retorna a URL de pagamento do Mercado Pago",
        responses = {
            @ApiResponse(responseCode = "200", description = "Checkout criado com sucesso",
                content = @Content(schema = @Schema(implementation = CheckoutResponse.class))),
            @ApiResponse(responseCode = "400", description = "Dados inválidos na requisição",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "502", description = "Falha na comunicação com o Mercado Pago",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
        }
    )
    public ResponseEntity<?> createCheckout(@Valid @RequestBody CheckoutRequest request) {
        try {
            CheckoutResponse response = purchaseService.createCheckout(request);
            return ResponseEntity.ok(response);
        } catch (MPApiException | MPException e) {
            // Log detalhado internamente; cliente recebe mensagem genérica e segura
            log.error("Falha ao criar preferência no Mercado Pago. productId={} error={}",
                      request.getProductId(), e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.BAD_GATEWAY)
                    .body(new ErrorResponse("Serviço de pagamento temporariamente indisponível. Tente novamente em instantes."));
        }
    }

    // ── GET /api/purchases/{id} ────────────────────────────────────────────────

    @GetMapping("/purchases/{id}")
    @Operation(
        summary = "Consultar status de compra",
        description = "Retorna o status atual de uma compra. Não expõe dados pessoais do comprador.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Status encontrado",
                content = @Content(schema = @Schema(implementation = PurchaseStatusResponse.class))),
            @ApiResponse(responseCode = "404", description = "Compra não encontrada")
        }
    )
    public ResponseEntity<PurchaseStatusResponse> getPurchaseStatus(@PathVariable Long id) {
        return purchaseService.getPurchaseStatus(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Endpoints de retorno (redirect do Mercado Pago) ────────────────────────

    @GetMapping("/return/success")
    @Operation(summary = "Retorno pós-pagamento — aprovado", hidden = true)
    public ResponseEntity<Void> success() {
        return redirect(frontendUrl + "/success");
    }

    @GetMapping("/return/pending")
    @Operation(summary = "Retorno pós-pagamento — pendente", hidden = true)
    public ResponseEntity<Void> pending() {
        return redirect(frontendUrl + "/pending");
    }

    @GetMapping("/return/failure")
    @Operation(summary = "Retorno pós-pagamento — falha", hidden = true)
    public ResponseEntity<Void> failure() {
        return redirect(frontendUrl + "/failure");
    }

    private ResponseEntity<Void> redirect(String url) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(url))
                .build();
    }
}
