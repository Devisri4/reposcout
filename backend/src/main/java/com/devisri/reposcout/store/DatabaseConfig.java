package com.devisri.reposcout.store;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Chooses the database. With DATABASE_URL set (Neon gives {@code postgresql://user:pass@host/db?sslmode=require}),
 * that Postgres is used; otherwise the H2 settings in application.properties (in-memory, no setup).
 */
@Configuration
public class DatabaseConfig {

    /** A JDBC URL plus the user and password taken out of a postgres:// URL. */
    record Jdbc(String url, String user, String password) {
    }

    @Bean
    public DataSource dataSource(@Value("${reposcout.database-url:}") String databaseUrl, DataSourceProperties defaults) {
        HikariDataSource ds;
        if (databaseUrl == null || databaseUrl.isBlank()) {
            ds = defaults.initializeDataSourceBuilder().type(HikariDataSource.class).build();
        } else {
            Jdbc jdbc = toJdbc(databaseUrl.strip());
            ds = new HikariDataSource();
            ds.setJdbcUrl(jdbc.url());
            ds.setUsername(jdbc.user());
            ds.setPassword(jdbc.password());
        }
        ds.setMaximumPoolSize(3); // free Neon plan and a 512 MB server: a few connections are plenty
        ds.setPoolName("reposcout");
        return ds;
    }

    /** Accepts postgres://, postgresql:// (Neon, Render, Supabase style) or a ready jdbc: URL. */
    static Jdbc toJdbc(String url) {
        if (url.startsWith("jdbc:")) {
            return new Jdbc(url, null, null);
        }
        if (!url.startsWith("postgres://") && !url.startsWith("postgresql://")) {
            throw new IllegalArgumentException("DATABASE_URL must start with postgresql://, postgres:// or jdbc:");
        }
        URI uri = URI.create(url.replaceFirst("^postgres(ql)?://", "http://"));
        String user = null;
        String password = null;
        if (uri.getRawUserInfo() != null) {
            String[] parts = uri.getRawUserInfo().split(":", 2);
            user = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            password = parts.length > 1 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : null;
        }
        String port = uri.getPort() > 0 ? ":" + uri.getPort() : "";
        String query = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
        return new Jdbc("jdbc:postgresql://" + uri.getHost() + port + uri.getRawPath() + query, user, password);
    }
}
