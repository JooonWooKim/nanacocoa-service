package com.nanacocoa.server.migration;

import java.sql.DriverManager;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SupportReplyNotificationMigrationTest {
    @Test void v17PreservesOrderNotificationsAndEnforcesOneSubjectPerReply() throws Exception {
        String mysql = System.getenv("SUPPORT_NOTIFY_MIGRATION_DB_URL");
        if (mysql != null && !mysql.startsWith("jdbc:mysql://127.0.0.1:")) {
            throw new IllegalArgumentException("Loopback DB required");
        }
        String url = mysql == null
                ? "jdbc:h2:mem:support-notify-upgrade;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1" : mysql;
        String user = mysql == null ? "sa" : "root";
        Flyway.configure().dataSource(url, user, "").locations("classpath:db/migration").target("16").load().migrate();
        try (var connection = DriverManager.getConnection(url, user, ""); var sql = connection.createStatement()) {
            sql.executeUpdate("insert into members (id,email,name,password,is_admin,created_at) values (1,'fixture@example.test','fixture','encoded',false,current_timestamp)");
            sql.executeUpdate("insert into orders (id,order_id,member_id,total_amount,status,version,created_at,updated_at) values (1,'historical',1,42000,'PAID',0,current_timestamp,current_timestamp)");
            sql.executeUpdate("""
                insert into order_notifications (id,order_id,member_id,order_number,event_type,confirmed_at,status,attempt_count,cycle_attempts,read_at)
                values (1,1,1,'historical','PAYMENT_COMPLETED',current_timestamp,'PUBLISHED',1,1,current_timestamp)
                """);
            sql.executeUpdate("""
                insert into support_inquiries (id,member_id,request_key,title,body,status,created_at,reply_body,replied_by,replied_at)
                values (1,1,'historical','fixture','fixture','ANSWERED',current_timestamp,'fixture',1,current_timestamp)
                """);
        }
        var flyway = Flyway.configure().dataSource(url, user, "").locations("classpath:db/migration").target("17").load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
        flyway.validate();
        try (var connection = DriverManager.getConnection(url, user, ""); var sql = connection.createStatement()) {
            try (var orders = sql.executeQuery("select order_number, status, read_at, inquiry_id from order_notifications where id=1")) {
                assertThat(orders.next()).isTrue();
                assertThat(orders.getString(1)).isEqualTo("historical");
                assertThat(orders.getString(2)).isEqualTo("PUBLISHED");
                assertThat(orders.getObject(3)).isNotNull();
                assertThat(orders.getObject(4)).isNull();
            }
            try (var replies = sql.executeQuery("select notification_confirmed_at from support_inquiries where id=1")) {
                assertThat(replies.next()).isTrue();
                assertThat(replies.getObject(1)).isNull();
            }
            String columns = "insert into order_notifications (inquiry_id,order_id,order_number,member_id,event_type,confirmed_at,status,attempt_count,cycle_attempts) ";
            assertThatThrownBy(() -> sql.executeUpdate(columns
                    + "values (1,1,'historical',1,'SUPPORT_ANSWERED',current_timestamp,'PENDING',0,0)"))
                    .isInstanceOf(SQLException.class);
            String reply = columns + "values (1,null,null,1,'SUPPORT_ANSWERED',current_timestamp,'PENDING',0,0)";
            assertThat(sql.executeUpdate(reply)).isEqualTo(1);
            assertThatThrownBy(() -> sql.executeUpdate(reply)).isInstanceOf(SQLException.class);
        }
    }
}
