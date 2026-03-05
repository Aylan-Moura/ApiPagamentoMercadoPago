package com.aylan.apipagamento.repository;

import com.aylan.apipagamento.model.Purchase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {

    Optional<Purchase> findByPreferenceId(String preferenceId);
}
