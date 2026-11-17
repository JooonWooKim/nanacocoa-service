package com.nanacocoa.server.payment.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanacocoa.server.payment.pg.TossPaymentsClient;
import com.nanacocoa.server.payment.pg.TossPaymentsRestClient;
import com.nanacocoa.server.payment.service.PaymentRecoveryScheduler;
import com.nanacocoa.server.payment.service.PaymentRecoveryService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TossPaymentsConfigTest {
	private final ApplicationContextRunner defaults = new ApplicationContextRunner()
			.withUserConfiguration(TossPaymentsConfig.class, PaymentRecoveryScheduler.class)
			.withBean(ObjectMapper.class, ObjectMapper::new)
			.withBean(PaymentRecoveryService.class, () -> mock(PaymentRecoveryService.class));

	private final ApplicationContextRunner enabled = defaults.withPropertyValues(
			"toss-payments.enabled=true",
			"toss-payments.client-key=live_ck_Fixture",
			"toss-payments.secret-key=live_sk_" + "Fixture");

	@Test
	void defaultsKeepProviderAndRecoveryDisabledEvenWithRecoveryFlag() {
		defaults.withPropertyValues("payment.reconciliation.enabled=true").run(context -> {
			assertThat(context.getBean(TossPaymentsClient.class).isDisabled()).isTrue();
			assertThat(context).doesNotHaveBean(PaymentRecoveryScheduler.class);
		});
	}

	@Test
	void liveProviderRequiresExplicitProdWithoutTest() {
		String error = "TOSS_PAYMENTS_ENABLED requires the active prod profile without the test profile.";
		enabled.withPropertyValues("spring.profiles.default=prod")
				.run(context -> assertThat(context).getFailure().hasRootCauseMessage(error));
		enabled.withPropertyValues("spring.profiles.active=prod,test")
				.run(context -> assertThat(context).getFailure().hasRootCauseMessage(error));
	}

	@Test
	void rejectsMissingMismatchedAndTestKeys() {
		var prod = enabled.withPropertyValues("spring.profiles.active=prod");
		String error = "Toss Payments requires a live client key and secret key for the payment window.";
		for (String clientKey : new String[] {"", "test_ck_Fixture", "live_gck_Fixture", "live_ck_Fix ture"}) {
			prod.withPropertyValues("toss-payments.client-key=" + clientKey)
					.run(context -> assertThat(context).getFailure().hasRootCauseMessage(error));
		}
		prod.withPropertyValues("toss-payments.secret-key=")
				.run(context -> assertThat(context).getFailure().hasRootCauseMessage(error));
		prod.withPropertyValues("toss-payments.client-key=live_gck_Fixture",
						"toss-payments.secret-key=live_gsk_" + "Fixture")
				.run(context -> assertThat(context).getFailure().hasRootCauseMessage(error));
	}

	@Test
	void validatesTheExactProviderOriginWithoutEchoingIt() {
		enabled.withPropertyValues("spring.profiles.active=prod",
						"toss-payments.base-url=https://api.tosspayments.com@pg.invalid")
				.run(context -> assertThat(context).getFailure().hasRootCauseMessage(
						"TOSS_PAYMENTS_BASE_URL must be the official HTTPS Toss Payments API origin."));
	}

	@Test
	void productionCanEnableRecoverySeparately() {
		var prod = enabled.withPropertyValues("spring.profiles.active=mysql,prod");
		prod.run(context -> {
			assertThat(context.getBean(TossPaymentsClient.class)).isInstanceOf(TossPaymentsRestClient.class);
			assertThat(context).doesNotHaveBean(PaymentRecoveryScheduler.class);
		});
		prod.withPropertyValues("payment.reconciliation.enabled=true").run(context -> {
			assertThat(context.getBean(TossPaymentsClient.class).isDisabled()).isFalse();
			assertThat(context).hasSingleBean(PaymentRecoveryScheduler.class);
		});
	}
}
