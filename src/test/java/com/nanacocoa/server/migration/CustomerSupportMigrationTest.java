package com.nanacocoa.server.migration;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CustomerSupportMigrationTest {
    @Test void v15PreservesExistingOrdersAndLeavesAnEmptyInquiryStore() throws Exception {
        String mysql = System.getenv("SUPPORT_MIGRATION_DB_URL");
        if (mysql != null && !mysql.startsWith("jdbc:mysql://127.0.0.1:")) {
            throw new IllegalArgumentException("Loopback test database required");
        }
        String url = mysql == null ? "jdbc:h2:mem:support-upgrade;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1" : mysql;
        String user = mysql == null ? "sa" : "root";
        Flyway.configure().dataSource(url,user,"").locations("classpath:db/migration").target("14").load().migrate();
        try (var connection = DriverManager.getConnection(url,user,""); var sql = connection.createStatement()) {
            sql.executeUpdate("insert into members (id,email,name,password,is_admin,created_at) values (1,'fixture@example.test','fixture','encoded',false,current_timestamp)");
            sql.executeUpdate("insert into orders (id,order_id,member_id,total_amount,status,version,created_at,updated_at) values (1,'support-fixture',1,42000,'CREATED',0,current_timestamp,current_timestamp)");
        }
        var flyway = Flyway.configure().dataSource(url,user,"").locations("classpath:db/migration").load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
        flyway.validate();
        try (var connection = DriverManager.getConnection(url,user,""); var sql = connection.createStatement()) {
            try (var order = sql.executeQuery("select order_id,member_id,total_amount,status from orders where id=1")) {
                assertThat(order.next()).isTrue();
                assertThat(order.getString(1)).isEqualTo("support-fixture");
                assertThat(order.getInt(2)).isEqualTo(1);
                assertThat(order.getLong(3)).isEqualTo(42000);
                assertThat(order.getString(4)).isEqualTo("CREATED");
            }
            try (var inquiries = sql.executeQuery("select count(*) from support_inquiries")) {
                assertThat(inquiries.next()).isTrue();
                assertThat(inquiries.getInt(1)).isZero();
            }
        }
    }
}
