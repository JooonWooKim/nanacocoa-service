package com.nanacocoa.server.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.sql.*;
import static org.assertj.core.api.Assertions.*;

class FilePolicyConsentMigrationTest {
    @Test void preservesLegacyConsentAndAllowsFileOnlyConsent() throws Exception {
        String external = System.getenv("POLICY_FILE_MIGRATION_DB_URL");
        String url = external == null ? "jdbc:h2:mem:file-policy-migration;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1" : external;
        String user = external == null ? "sa" : "root";
        Flyway.configure().dataSource(url,user,"").locations("classpath:db/migration").target("9").load().migrate();
        try (var connection = DriverManager.getConnection(url,user,""); var sql = connection.createStatement()) {
            sql.executeUpdate("INSERT INTO members (id,email,name,password,is_admin,created_at) VALUES (901,'legacy-consent@example.test','fixture','encoded',false,CURRENT_TIMESTAMP)");
            sql.executeUpdate("INSERT INTO policy_documents (id,document_code,document_version,title,body,consent_mode,effective_at,approved_by,approved_at,content_hash,active) VALUES (901,'TERMS','legacy-v1','fixture','archived original','REQUIRED','2020-01-01 00:00:00','fixture','2020-01-01 00:00:00',REPEAT('a',64),true)");
            sql.executeUpdate("INSERT INTO member_policy_consents (id,member_id,document_id,agreed,recorded_at) VALUES (901,901,901,true,'2020-02-01 01:02:03')");
        }
        assertThat(Flyway.configure().dataSource(url,user,"").locations("classpath:db/migration").load().migrate().migrationsExecuted).isEqualTo(7);
        try (var connection = DriverManager.getConnection(url,user,""); var sql = connection.createStatement()) {
            try (var rows = sql.executeQuery("SELECT * FROM member_policy_consents WHERE id=901")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getLong("document_id")).isEqualTo(901);
                assertThat(rows.getString("document_code")).isEqualTo("TERMS");
                assertThat(rows.getString("document_version")).isEqualTo("legacy-v1");
                assertThat(rows.getString("content_hash")).isEqualTo("a".repeat(64));
                assertThat(rows.getBoolean("agreed")).isTrue();
                assertThat(rows.getTimestamp("recorded_at").toLocalDateTime()).isEqualTo(java.time.LocalDateTime.of(2020,2,1,1,2,3));
            }
            try (var rows = sql.executeQuery("SELECT body FROM policy_documents WHERE id=901")) {
                assertThat(rows.next()).isTrue(); assertThat(rows.getString(1)).isEqualTo("archived original");
            }
            String insert = "INSERT INTO member_policy_consents (member_id,document_code,document_version,content_hash,agreed,recorded_at) VALUES (901,'TERMS','file-v2',REPEAT('b',64),true,CURRENT_TIMESTAMP)";
            assertThat(sql.executeUpdate(insert)).isEqualTo(1);
            assertThatThrownBy(() -> sql.executeUpdate(insert)).isInstanceOf(SQLException.class);
            try (var rows = sql.executeQuery("SELECT document_id FROM member_policy_consents WHERE document_version='file-v2'")) {
                assertThat(rows.next()).isTrue(); assertThat(rows.getObject(1)).isNull();
            }
        }
    }
}
