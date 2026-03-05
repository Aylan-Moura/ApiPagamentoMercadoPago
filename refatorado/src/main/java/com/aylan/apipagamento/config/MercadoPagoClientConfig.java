package com.aylan.apipagamento.config;

import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.preference.PreferenceClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registra os clientes do SDK do Mercado Pago como Beans do Spring,
 * permitindo injeção de dependência e facilitando mocking em testes.
 */
@Configuration
public class MercadoPagoClientConfig {

    /**
     * Bean do PaymentClient do Mercado Pago.
     */
    @Bean
    public PaymentClient paymentClient() {
        return new PaymentClient();
    }

    /**
     * Bean do PreferenceClient do Mercado Pago.
     */
    @Bean
    public PreferenceClient preferenceClient() {
        return new PreferenceClient();
    }
}
