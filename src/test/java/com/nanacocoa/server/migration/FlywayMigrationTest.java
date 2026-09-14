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

		Flyway allMigrations = Flyway.configure()
				.dataSource(url, "sa", "")
				.locations("classpath:db/migration")
				.load();
		assertThat(allMigrations.migrate().migrationsExecuted).isEqualTo(1);

		try (Connection connection = DriverManager.getConnection(url, "sa", "");
			 Statement statement = connection.createStatement();
			 ResultSet resultSet = statement.executeQuery("select count(*) from payments")) {
			assertThat(resultSet.next()).isTrue();
			assertThat(resultSet.getLong(1)).isZero();
		}

		try (Connection connection = DriverManager.getConnection(url, "sa", "");
			 Statement statement = connection.createStatement();
			 ResultSet resultSet = statement.executeQuery("select recipient_name, phone_number, shipping_address, customer_request from orders")) {
			assertThat(resultSet.next()).isFalse();
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
