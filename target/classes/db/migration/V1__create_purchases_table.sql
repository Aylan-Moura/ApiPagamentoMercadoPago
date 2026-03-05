-- =============================================================================
-- V1__create_purchases_table.sql
-- Schema inicial da tabela de compras.
-- Gerado pelo Flyway — nunca edite migrações já aplicadas em produção.
-- =============================================================================

CREATE TABLE IF NOT EXISTS purchases (
    id           BIGSERIAL    PRIMARY KEY,
    email        VARCHAR(255) NOT NULL,
    product_id   VARCHAR(255) NOT NULL,
    preference_id VARCHAR(255),
    payment_id   VARCHAR(255),
    status       VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Índices para queries frequentes
CREATE INDEX IF NOT EXISTS idx_purchases_preference_id ON purchases (preference_id);
CREATE INDEX IF NOT EXISTS idx_purchases_payment_id    ON purchases (payment_id);
CREATE INDEX IF NOT EXISTS idx_purchases_status        ON purchases (status);

COMMENT ON TABLE  purchases                  IS 'Registra cada tentativa de compra e seu status no Mercado Pago';
COMMENT ON COLUMN purchases.preference_id    IS 'ID da preferência criada na API do Mercado Pago';
COMMENT ON COLUMN purchases.payment_id       IS 'ID do pagamento confirmado via webhook';
COMMENT ON COLUMN purchases.status           IS 'PENDING | APPROVED | REJECTED | CANCELLED';
