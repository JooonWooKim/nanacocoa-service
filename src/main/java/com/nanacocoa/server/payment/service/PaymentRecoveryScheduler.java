package com.nanacocoa.server.payment.service;

import com.nanacocoa.server.common.observability.IncidentTrace;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
		name = {"toss-payments.enabled", "payment.reconciliation.enabled"},
		havingValue = "true"
)
public class PaymentRecoveryScheduler {
	private final PaymentRecoveryService recoveryService;
	private final int batchSize;

	public PaymentRecoveryScheduler(
			PaymentRecoveryService recoveryService,
			@Value("${payment.reconciliation.batch-size:100}") int batchSize) {
		this.recoveryService = recoveryService;
		this.batchSize = batchSize;
	}

	@Scheduled(
			fixedDelayString = "${payment.reconciliation.fixed-delay:10s}",
			initialDelayString = "${payment.reconciliation.initial-delay:30s}"
	)
	public void reconcile() {
		try (var trace = IncidentTrace.recoveryRun()) {
			recoveryService.reconcileDueApprovals(batchSize);
			recoveryService.reconcileDueCancellations(batchSize);
			IncidentTrace.mark(IncidentTrace.Result.SUCCESS);
		} catch (RuntimeException exception) {
			throw new IllegalStateException("Payment recovery batch failed");
		}
	}
}
