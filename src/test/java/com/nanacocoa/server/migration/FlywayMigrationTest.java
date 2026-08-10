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
	void appliesBaselineAndPaymentMigrations() throws Exception {
		String url = "jdbc:h2:mem:flyway-migration;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
		Flyway flyway = Flyway.configure()
				.dataSource(url, "sa", "")
				.locations("classpath:db/migration")
				.load();

		assertThat(flyway.migrate().migrationsExecuted).isEqualTo(3);

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
	}
}
