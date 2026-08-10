package com.nanacocoa.server.migration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(
		properties = {
				"spring.datasource.url=jdbc:h2:mem:jpa-schema-validation;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
				"spring.datasource.driver-class-name=org.h2.Driver",
				"spring.datasource.username=sa",
				"spring.datasource.password=",
				"spring.flyway.enabled=true",
				"spring.jpa.hibernate.ddl-auto=validate",
				"spring.jpa.open-in-view=false",
				"spring.sql.init.mode=never",
				"payment.reconciliation.enabled=false"
		}
)
class JpaSchemaValidationTest {
	@Test
	void applicationStartsWithFlywaySchemaValidation() {
	}
}
