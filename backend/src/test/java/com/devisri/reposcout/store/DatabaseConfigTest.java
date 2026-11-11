package com.devisri.reposcout.store;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatabaseConfigTest {

    @Test
    void acceptsTheConnectionStringExactlyAsNeonShowsIt() {
        var jdbc = DatabaseConfig.toJdbc(
                "postgresql://devi_owner:p%40ss%2Fw0rd@ep-cool-name-123.ap-southeast-1.aws.neon.tech/reposcout?sslmode=require&channel_binding=require");

        assertThat(jdbc.url()).isEqualTo(
                "jdbc:postgresql://ep-cool-name-123.ap-southeast-1.aws.neon.tech/reposcout?sslmode=require&channel_binding=require");
        assertThat(jdbc.user()).isEqualTo("devi_owner");
        assertThat(jdbc.password()).isEqualTo("p@ss/w0rd");
    }

    @Test
    void keepsAPortAndAcceptsTheShortPostgresScheme() {
        var jdbc = DatabaseConfig.toJdbc("postgres://u:p@db.example.com:6543/app");

        assertThat(jdbc.url()).isEqualTo("jdbc:postgresql://db.example.com:6543/app");
        assertThat(jdbc.user()).isEqualTo("u");
    }

    @Test
    void passesAJdbcUrlThroughUnchanged() {
        assertThat(DatabaseConfig.toJdbc("jdbc:postgresql://localhost/app").url()).isEqualTo("jdbc:postgresql://localhost/app");
    }

    @Test
    void rejectsAnythingElse() {
        assertThatThrownBy(() -> DatabaseConfig.toJdbc("mysql://u:p@host/db")).isInstanceOf(IllegalArgumentException.class);
    }
}
