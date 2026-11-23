package com.nanacocoa.server.migration;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ProductDiscoveryMigrationTest {
    @Test void v14PreservesLegacyDataAndSeedsStableCategories() throws Exception {
        String mysql = System.getenv("DISCOVERY_MIGRATION_DB_URL");
        if (mysql != null && !mysql.startsWith("jdbc:mysql://127.0.0.1:")) {
            throw new IllegalArgumentException("Loopback test database required");
        }
        String url = mysql == null
                ? "jdbc:h2:mem:discovery-upgrade;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1" : mysql;
        String user = mysql == null ? "sa" : "root";
        Flyway.configure().dataSource(url, user, "").locations("classpath:db/migration").target("13").load().migrate();
        try (var connection = DriverManager.getConnection(url, user, ""); var sql = connection.createStatement()) {
            sql.executeUpdate("""
                insert into members (id,email,name,password,is_admin,created_at)
                values (1,'synthetic@example.test','Synthetic','fixture',false,current_timestamp)
                """);
            sql.executeUpdate("""
                insert into products (id,name,price,summary,detail_title,description,created_at,stock_quantity,reserved_quantity,sale_status,stock_version)
                values (1,'Legacy',41000,'synthetic','synthetic','synthetic',current_timestamp,7,2,'ON_SALE',4)
                """);
            sql.executeUpdate("""
                insert into orders (id,order_id,member_id,total_amount,status,version,created_at,updated_at)
                values (1,'discovery-fixture',1,41000,'CREATED',0,current_timestamp,current_timestamp)
                """);
        }
        var flyway = Flyway.configure().dataSource(url, user, "").locations("classpath:db/migration").load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(3);
        flyway.validate();
        try (var connection = DriverManager.getConnection(url, user, ""); var sql = connection.createStatement()) {
            try (var product = sql.executeQuery("""
                select category_id,price,stock_quantity,reserved_quantity,sale_status,stock_version from products where id=1
                """)) {
                assertThat(product.next()).isTrue();
                assertThat(product.getObject("category_id")).isNull();
                assertThat(product.getLong("price")).isEqualTo(41000);
                assertThat(product.getLong("stock_quantity")).isEqualTo(7);
                assertThat(product.getLong("reserved_quantity")).isEqualTo(2);
                assertThat(product.getString("sale_status")).isEqualTo("ON_SALE");
                assertThat(product.getLong("stock_version")).isEqualTo(4);
            }
            try (var order = sql.executeQuery("select total_amount,status from orders where id=1")) {
                assertThat(order.next()).isTrue();
                assertThat(order.getLong(1)).isEqualTo(41000);
                assertThat(order.getString(2)).isEqualTo("CREATED");
            }
            try (var categories = sql.executeQuery("select id,name from product_categories where visible=true order by sort_order,id")) {
                String[] names = {"Mugs", "Tableware", "One Piece"};
                for (int i = 0; i < names.length; i++) {
                    assertThat(categories.next()).isTrue();
                    assertThat(categories.getLong(1)).isEqualTo(i + 1);
                    assertThat(categories.getString(2)).isEqualTo(names[i]);
                }
                assertThat(categories.next()).isFalse();
            }
        }
    }
}
