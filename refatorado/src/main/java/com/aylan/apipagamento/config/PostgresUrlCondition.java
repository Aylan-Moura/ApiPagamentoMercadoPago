package com.aylan.apipagamento.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Ativa o {@link DataSourceConfig} apenas quando {@code DATABASE_URL}
 * está no formato nativo do Render ({@code postgresql://} ou {@code postgres://}).
 */
public class PostgresUrlCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String url = context.getEnvironment().getProperty("DATABASE_URL", "");
        return url.startsWith("postgresql://") || url.startsWith("postgres://");
    }
}
