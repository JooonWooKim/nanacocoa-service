package com.nanacocoa.server.payment.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
		name = "payment.reconciliation.enabled",
		havingValue = "true",
		matchIfMissing = true
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
		recoveryService.reconcileDueApprovals(batchSize);
		recoveryService.reconcileDueCancellations(batchSize);
	}
}
