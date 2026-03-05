package com.aylan.apipagamento.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("WebhookSignatureValidator")
class WebhookSignatureValidatorTest {

    private WebhookSignatureValidator validator;

    private static final String SECRET    = "test-secret-key-12345";
    private static final String DATA_ID   = "123456789";
    private static final String REQUEST_ID = "req-abc-001";
    private static final String TS        = "1708950000";

    @BeforeEach
    void setUp() {
        validator = new WebhookSignatureValidator();
        ReflectionTestUtils.setField(validator, "webhookSecret", SECRET);
    }

    // ── Fail-secure: sem secret configurado ──────────────────────────────────

    @Nested
    @DisplayName("Quando secret não está configurado")
    class SemSecret {

        @Test
        @DisplayName("deve rejeitar sempre (fail-secure) quando secret está vazio")
        void deveRejeitarQuandoSecretVazio() {
            ReflectionTestUtils.setField(validator, "webhookSecret", "");
            assertThat(validator.isValid(buildSignature(SECRET, DATA_ID, REQUEST_ID, TS), REQUEST_ID, DATA_ID))
                    .isFalse();
        }

        @Test
        @DisplayName("deve rejeitar sempre (fail-secure) quando secret é null")
        void deveRejeitarQuandoSecretNull() {
            ReflectionTestUtils.setField(validator, "webhookSecret", null);
            assertThat(validator.isValid(buildSignature(SECRET, DATA_ID, REQUEST_ID, TS), REQUEST_ID, DATA_ID))
                    .isFalse();
        }
    }

    // ── Assinatura válida ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Quando assinatura é válida")
    class AssinaturaValida {

        @Test
        @DisplayName("deve aprovar assinatura HMAC-SHA256 correta")
        void deveAprovarAssinaturaCorreta() {
            String xSignature = buildSignature(SECRET, DATA_ID, REQUEST_ID, TS);
            assertThat(validator.isValid(xSignature, REQUEST_ID, DATA_ID)).isTrue();
        }

        @Test
        @DisplayName("deve aceitar dataId em maiúsculas (normaliza para minúsculo)")
        void deveAceitarDataIdEmMaiusculas() {
            String xSignature = buildSignature(SECRET, DATA_ID.toLowerCase(), REQUEST_ID, TS);
            assertThat(validator.isValid(xSignature, REQUEST_ID, DATA_ID.toUpperCase())).isTrue();
        }
    }

    // ── Assinatura inválida ───────────────────────────────────────────────────

    @Nested
    @DisplayName("Quando assinatura é inválida")
    class AssinaturaInvalida {

        @Test
        @DisplayName("deve rejeitar quando o hash v1 não confere")
        void deveRejeitarHashIncorreto() {
            String xSignature = "ts=" + TS + ",v1=0000000000000000000000000000000000000000000000000000000000000000";
            assertThat(validator.isValid(xSignature, REQUEST_ID, DATA_ID)).isFalse();
        }

        @Test
        @DisplayName("deve rejeitar quando o secret usado para assinar é diferente")
        void deveRejeitarSecretDiferente() {
            String xSignature = buildSignature("outro-secret", DATA_ID, REQUEST_ID, TS);
            assertThat(validator.isValid(xSignature, REQUEST_ID, DATA_ID)).isFalse();
        }

        @Test
        @DisplayName("deve rejeitar quando o dataId é diferente do assinado")
        void deveRejeitarDataIdDiferente() {
            String xSignature = buildSignature(SECRET, "outro-id", REQUEST_ID, TS);
            assertThat(validator.isValid(xSignature, REQUEST_ID, DATA_ID)).isFalse();
        }
    }

    // ── Headers ausentes ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Quando headers estão ausentes ou malformados")
    class HeadersAusentes {

        @Test
        @DisplayName("deve rejeitar quando x-signature é null")
        void deveRejeitarSemXSignature() {
            assertThat(validator.isValid(null, REQUEST_ID, DATA_ID)).isFalse();
        }

        @Test
        @DisplayName("deve rejeitar quando x-signature está em branco")
        void deveRejeitarXSignatureEmBranco() {
            assertThat(validator.isValid("  ", REQUEST_ID, DATA_ID)).isFalse();
        }

        @Test
        @DisplayName("deve rejeitar quando x-request-id é null")
        void deveRejeitarSemRequestId() {
            String xSignature = buildSignature(SECRET, DATA_ID, REQUEST_ID, TS);
            assertThat(validator.isValid(xSignature, null, DATA_ID)).isFalse();
        }

        @Test
        @DisplayName("deve rejeitar quando data.id é null")
        void deveRejeitarSemDataId() {
            String xSignature = buildSignature(SECRET, DATA_ID, REQUEST_ID, TS);
            assertThat(validator.isValid(xSignature, REQUEST_ID, null)).isFalse();
        }

        @Test
        @DisplayName("deve rejeitar quando x-signature está malformado (sem ts)")
        void deveRejeitarSemTs() {
            assertThat(validator.isValid("v1=abc123", REQUEST_ID, DATA_ID)).isFalse();
        }

        @Test
        @DisplayName("deve rejeitar quando x-signature está malformado (sem v1)")
        void deveRejeitarSemV1() {
            assertThat(validator.isValid("ts=" + TS, REQUEST_ID, DATA_ID)).isFalse();
        }
    }

    // ── Utilitário para gerar assinaturas nos testes ──────────────────────────

    private static String buildSignature(String secret, String dataId, String requestId, String ts) {
        try {
            String manifest = "id:" + dataId.toLowerCase() + ";request-id:" + requestId + ";ts:" + ts + ";";
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String hash = HexFormat.of().formatHex(mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8)));
            return "ts=" + ts + ",v1=" + hash;
        } catch (Exception e) {
            throw new RuntimeException("Erro ao gerar assinatura no teste", e);
        }
    }
}
