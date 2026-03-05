package com.aylan.apipagamento.repository;

import com.aylan.apipagamento.model.Purchase;
import com.aylan.apipagamento.model.PurchaseStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste de integração do repositório usando PostgreSQL real via Testcontainers.
 * Garante que queries, índices e mapeamentos JPA funcionam corretamente.
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("PurchaseRepository")
class PurchaseRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("apipagamento_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // Em testes @DataJpaTest, usa ddl-auto=create-drop para criar o schema
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @Autowired
    private PurchaseRepository purchaseRepository;

    @Test
    @DisplayName("deve salvar e recuperar uma Purchase por ID")
    void deveSalvarERecuperarPorId() {
        Purchase purchase = criarPurchase("test@email.com", "CURSO-01", "pref-001");
        Purchase saved = purchaseRepository.save(purchase);

        Optional<Purchase> found = purchaseRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("test@email.com");
        assertThat(found.get().getStatus()).isEqualTo(PurchaseStatus.PENDING);
    }

    @Test
    @DisplayName("deve encontrar Purchase por preferenceId")
    void deveEncontrarPorPreferenceId() {
        purchaseRepository.save(criarPurchase("a@email.com", "CURSO-01", "pref-aaa"));
        purchaseRepository.save(criarPurchase("b@email.com", "CURSO-01", "pref-bbb"));

        Optional<Purchase> found = purchaseRepository.findByPreferenceId("pref-aaa");

        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("a@email.com");
    }

    @Test
    @DisplayName("deve retornar Optional vazio para preferenceId inexistente")
    void deveRetornarVazioParaPreferenceIdInexistente() {
        Optional<Purchase> found = purchaseRepository.findByPreferenceId("nao-existe");
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("deve persistir e recuperar o status atualizado")
    void devePersistirStatusAtualizado() {
        Purchase purchase = purchaseRepository.save(criarPurchase("c@email.com", "CURSO-01", "pref-ccc"));
        assertThat(purchase.getStatus()).isEqualTo(PurchaseStatus.PENDING);

        purchase.setStatus(PurchaseStatus.APPROVED);
        purchase.setPaymentId("pay-999");
        purchaseRepository.save(purchase);

        Purchase updated = purchaseRepository.findById(purchase.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(PurchaseStatus.APPROVED);
        assertThat(updated.getPaymentId()).isEqualTo("pay-999");
    }

    @Test
    @DisplayName("deve preencher createdAt e updatedAt automaticamente")
    void devePreencherTimestamps() {
        Purchase purchase = purchaseRepository.save(criarPurchase("d@email.com", "CURSO-01", "pref-ddd"));

        assertThat(purchase.getCreatedAt()).isNotNull();
        assertThat(purchase.getUpdatedAt()).isNotNull();
    }

    private Purchase criarPurchase(String email, String productId, String preferenceId) {
        Purchase p = new Purchase();
        p.setEmail(email);
        p.setProductId(productId);
        p.setPreferenceId(preferenceId);
        p.setStatus(PurchaseStatus.PENDING);
        return p;
    }
}
