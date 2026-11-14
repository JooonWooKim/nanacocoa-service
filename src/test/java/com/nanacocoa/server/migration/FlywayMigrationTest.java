package com.nanacocoa.server.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationTest {
	@Test
	void appliesMigrationsAndPreservesExistingMemberProfileDefaults() throws Exception {
		String url = "jdbc:h2:mem:flyway-migration;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
		Flyway firstThreeMigrations = Flyway.configure()
				.dataSource(url, "sa", "")
				.locations("classpath:db/migration")
				.target("3")
				.load();

		assertThat(firstThreeMigrations.migrate().migrationsExecuted).isEqualTo(3);

		try (Connection connection = DriverManager.getConnection(url, "sa", "");
			 Statement statement = connection.createStatement()) {
			statement.executeUpdate("""
					insert into members (email, name, password, is_admin, created_at)
					values ('legacy@example.com', '기존 회원', 'encoded', false, current_timestamp)
					""");
		}

		try (Connection connection = DriverManager.getConnection(url, "sa", ""); Statement statement = connection.createStatement()) {
			statement.executeUpdate("insert into products (name, price, summary, detail_title, description, created_at) values ('legacy product', 41000, 'summary', 'detail', 'description', current_timestamp)");
			statement.executeUpdate("insert into orders (order_id, member_id, total_amount, status, version, created_at, updated_at) select 'legacy-order', id, 41000, 'CREATED', 0, current_timestamp, current_timestamp from members where email = 'legacy@example.com'");
		}

		Flyway allMigrations = Flyway.configure()
				.dataSource(url, "sa", "")
				.locations("classpath:db/migration")
				.load();
		assertThat(allMigrations.migrate().migrationsExecuted).isEqualTo(2);

		try (Connection connection = DriverManager.getConnection(url, "sa", "");
			 Statement statement = connection.createStatement();
			 ResultSet resultSet = statement.executeQuery("select count(*) from payments")) {
			assertThat(resultSet.next()).isTrue();
			assertThat(resultSet.getLong(1)).isZero();
		}

		try (Connection connection = DriverManager.getConnection(url, "sa", "");
			 Statement statement = connection.createStatement();
			 ResultSet resultSet = statement.executeQuery("select stock_state, reservation_expires_at from orders where order_id = 'legacy-order'")) {
			assertThat(resultSet.next()).isTrue();
			assertThat(resultSet.getString("stock_state")).isEqualTo("LEGACY");
			assertThat(resultSet.getObject("reservation_expires_at")).isNull();
		}

		try (Connection connection = DriverManager.getConnection(url, "sa", ""); Statement statement = connection.createStatement();
			 ResultSet resultSet = statement.executeQuery("select stock_quantity, reserved_quantity, sale_status from products where name = 'legacy product'")) {
			assertThat(resultSet.next()).isTrue();
			assertThat(resultSet.getLong("stock_quantity")).isZero();
			assertThat(resultSet.getLong("reserved_quantity")).isZero();
			assertThat(resultSet.getString("sale_status")).isEqualTo("STOPPED");
		}

		try (Connection connection = DriverManager.getConnection(url, "sa", "");
			 Statement statement = connection.createStatement();
			 ResultSet resultSet = statement.executeQuery("select address, phone_number, email_receive_consent from members where email = 'legacy@example.com'")) {
			assertThat(resultSet.next()).isTrue();
			assertThat(resultSet.getString("address")).isEmpty();
			assertThat(resultSet.getString("phone_number")).isEmpty();
			assertThat(resultSet.getString("email_receive_consent")).isEqualTo("N");
		}
	}
}
