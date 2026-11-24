package com.nanacocoa.server.migration;

import java.sql.DriverManager;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestockNotificationMigrationTest {
    @Test void v18PreservesPublishedSubjectsAndConstrainsNewSubscriptions() throws Exception {
        String mysql = System.getenv("RESTOCK_NOTIFY_MIGRATION_DB_URL");
        if (mysql != null && !mysql.startsWith("jdbc:mysql://127.0.0.1:")) {
            throw new IllegalArgumentException("Loopback DB required");
        }
        String url = mysql == null
                ? "jdbc:h2:mem:restock-notify-upgrade;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1" : mysql;
        String user = mysql == null ? "sa" : "root";
        Flyway.configure().dataSource(url, user, "").locations("classpath:db/migration").target("17").load().migrate();
        try (var connection = DriverManager.getConnection(url, user, ""); var sql = connection.createStatement()) {
            sql.executeUpdate("insert into members (id,email,name,password,is_admin,created_at) values (1,'fixture@example.test','fixture','encoded',false,current_timestamp)");
            sql.executeUpdate("insert into orders (id,order_id,member_id,total_amount,status,version,created_at,updated_at) values (1,'historical',1,42000,'PAID',0,current_timestamp,current_timestamp)");
            sql.executeUpdate("insert into support_inquiries (id,member_id,request_key,title,body,status,created_at) values (1,1,'historical','fixture','fixture','ANSWERED',current_timestamp)");
            sql.executeUpdate("""
                insert into order_notifications (id,order_id,order_number,inquiry_id,member_id,event_type,confirmed_at,status,attempt_count,cycle_attempts,read_at)
                values (1,1,'historical',null,1,'PAYMENT_COMPLETED',current_timestamp,'PUBLISHED',1,1,current_timestamp),
                       (2,null,null,1,1,'SUPPORT_ANSWERED',current_timestamp,'PUBLISHED',1,1,current_timestamp)
                """);
        }
        var flyway = Flyway.configure().dataSource(url, user, "").locations("classpath:db/migration").load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
        flyway.validate();
        try (var connection = DriverManager.getConnection(url, user, ""); var sql = connection.createStatement()) {
            try (var previous = sql.executeQuery("select count(*) from order_notifications where status='PUBLISHED' and read_at is not null and product_id is null")) {
                assertThat(previous.next()).isTrue();
                assertThat(previous.getInt(1)).isEqualTo(2);
            }
            try (var olderProducts = sql.executeQuery("select count(*) from products where availability_epoch <> 0")) {
                assertThat(olderProducts.next()).isTrue();
                assertThat(olderProducts.getInt(1)).isZero();
            }
            sql.executeUpdate("insert into products (id,name,price,summary,detail_title,description,created_at) values (9999,'fixture',1,'fixture','fixture','fixture',current_timestamp)");
            sql.executeUpdate("insert into product_restock_subscriptions (id, product_id, member_id, subscribed_epoch, active) values (1,9999,1,0,true)");
            assertThatThrownBy(() -> sql.executeUpdate("insert into product_restock_subscriptions (id, product_id, member_id, subscribed_epoch, active) values (2,9999,1,0,true)"))
                    .isInstanceOf(SQLException.class);
            sql.executeUpdate("update product_restock_subscriptions set active=null where id=1");
            sql.executeUpdate("insert into product_restock_subscriptions (id, product_id, member_id, subscribed_epoch, active) values (2,9999,1,2,true)");
            String insert = "insert into order_notifications (restock_subscription_id, product_id, order_id, order_number, member_id,event_type,confirmed_at,status,attempt_count,cycle_attempts) ";
            assertThatThrownBy(() -> sql.executeUpdate(insert + "values (2,9999,1,'historical',1,'PRODUCT_RESTOCKED',current_timestamp,'PENDING',0,0)"))
                    .isInstanceOf(SQLException.class);
            String notification = insert + "values (2,9999,null,null,1,'PRODUCT_RESTOCKED',current_timestamp,'PENDING',0,0)";
            assertThat(sql.executeUpdate(notification)).isEqualTo(1);
            assertThatThrownBy(() -> sql.executeUpdate(notification)).isInstanceOf(SQLException.class);
        }
    }
}
