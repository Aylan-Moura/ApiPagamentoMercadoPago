package com.aylan.apipagamento.config;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Converte {@code DATABASE_URL} no formato do Render ({@code postgresql://user:pass@host/db})
 * para um {@link HikariDataSource} JDBC.
 *
 * <p>Ativo apenas quando {@code DATABASE_URL} inicia com {@code postgresql://} ou
 * {@code postgres://} (condição avaliada por {@link PostgresUrlCondition}).
 * Caso contrário, o Spring usa a configuração padrão do {@code application.properties}.
 */
@Slf4j
@Configuration
public class DataSourceConfig {

    @Value("${DATABASE_URL:}")
    private String databaseUrl;

    @Bean
    @Primary
    @Conditional(PostgresUrlCondition.class)
    public DataSource dataSource() {
        try {
            URI uri = new URI(databaseUrl.trim().replace("postgresql://", "postgres://"));

            String host     = uri.getHost();
            int    port     = uri.getPort() > 0 ? uri.getPort() : 5432;
            String database = uri.getPath() != null && uri.getPath().length() > 1
                              ? uri.getPath().substring(1) : "postgres";

            String username = null;
            String password = null;
            if (uri.getUserInfo() != null) {
                int colon = uri.getUserInfo().indexOf(':');
                if (colon > 0) {
                    username = decode(uri.getUserInfo().substring(0, colon));
                    password = decode(uri.getUserInfo().substring(colon + 1));
                } else {
                    username = decode(uri.getUserInfo());
                }
            }

            String jdbcUrl = "jdbc:postgresql://" + host + ":" + port + "/" + database;
            if (databaseUrl.contains("sslmode=") || databaseUrl.contains("ssl=")) {
                jdbcUrl += "?sslmode=require";
            }

            HikariDataSource ds = new HikariDataSource();
            ds.setJdbcUrl(jdbcUrl);
            if (username != null) ds.setUsername(username);
            if (password != null) ds.setPassword(password);
            ds.setDriverClassName("org.postgresql.Driver");

            // Log sem expor credenciais
            log.info("DataSource configurado via DATABASE_URL. host={} database={}", host, database);
            return ds;

        } catch (Exception e) {
            throw new IllegalStateException("DATABASE_URL inválida: " + e.getMessage(), e);
        }
    }

    private static String decode(String s) {
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }
}
