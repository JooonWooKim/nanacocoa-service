package com.nanacocoa.server.common.observability;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanacocoa.server.payment.service.PaymentRecoveryScheduler;
import com.nanacocoa.server.payment.service.PaymentRecoveryService;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static com.nanacocoa.server.common.observability.IncidentTrace.Operation.RECOVERY_APPROVAL;
import static com.nanacocoa.server.common.observability.IncidentTrace.Result.PENDING;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class IncidentTraceTest {
    private final Logger logger = (Logger) LoggerFactory.getLogger("nanacocoa.incident");
    private final ListAppender<ILoggingEvent> events = new ListAppender<>();
    private final IncidentRequestFilter filter = new IncidentRequestFilter();
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach void captureEvents() {
        events.start();
        logger.addAppender(events);
    }

    @AfterEach void releaseEvents() {
        logger.detachAppender(events);
        events.stop();
    }

    private JsonNode event(int index) throws Exception {
        return json.readTree(events.list.get(index).getFormattedMessage().substring("INCIDENT_EVENT ".length()));
    }

    @Test void failureAndParallelRequestsUseIsolatedServerIds() throws Exception {
        var broken = new MockHttpServletRequest("POST", "/api/payments/customer-marker/cancel");
        broken.setQueryString("paymentKey=secret-marker");
        broken.addHeader("X-Request-Id", "spoof-marker");
        var response = new MockHttpServletResponse();
        assertThatThrownBy(() -> filter.doFilter(broken, response, (req, res) -> {
            throw new IllegalStateException("exception-marker");
        })).isInstanceOf(ServletException.class).hasNoCause();
        assertThat(event(0).path("id").asText()).isEqualTo(response.getHeader("X-Request-Id"));
        assertThat(event(0).path("httpStatus").asInt()).isEqualTo(500);
        assertThat(event(0).path("result").asText()).isEqualTo("INTERNAL_ERROR");

        var tasks = new ArrayList<Callable<String>>();
        for (int i = 0; i < 8; i++) {
            tasks.add(() -> {
                var next = new MockHttpServletResponse();
                filter.doFilter(new MockHttpServletRequest("POST", "/api/orders"), next,
                        (req, res) -> assertThat(((MockHttpServletResponse) res).getHeader("X-Request-Id")).isNotEmpty());
                return next.getHeader("X-Request-Id");
            });
        }
        var pool = Executors.newFixedThreadPool(2);
        try {
            List<String> ids = new ArrayList<>();
            for (var future : pool.invokeAll(tasks)) ids.add(future.get());
            assertThat(ids).doesNotHaveDuplicates().allSatisfy(id -> assertThat(UUID.fromString(id).version()).isEqualTo(4));
            for (int i = 1; i < events.list.size(); i++) {
                assertThat(ids).contains(event(i).path("id").asText());
                assertThat(event(i).path("result").asText()).isEqualTo("SUCCESS");
            }
        } finally {
            pool.shutdownNow();
        }
        var lookup = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/api/orders"), lookup, (req, res) -> {});
        assertThat(lookup.getHeader("X-Request-Id")).isNull();
        assertThat(events.list).hasSize(9).allSatisfy(log -> assertThat(log.getFormattedMessage()).doesNotContain("-marker"));
    }

    @Test void schedulerKeepsBatchAndAttemptsTogetherAndClearsContext() throws Exception {
        var recovery = mock(PaymentRecoveryService.class);
        doAnswer(call -> {
            try (var attempt = IncidentTrace.recoveryAttempt(RECOVERY_APPROVAL)) {
                IncidentTrace.markCompensation();
                IncidentTrace.mark(PENDING);
            }
            return null;
        }).when(recovery).reconcileDueApprovals(10);
        var scheduler = new PaymentRecoveryScheduler(recovery, 10);
        scheduler.reconcile();
        scheduler.reconcile();
        assertThat(events.list).hasSize(4);
        assertThat(event(0).path("operation").asText()).isEqualTo("RECOVERY_COMPENSATION");
        assertThat(event(0).path("result").asText()).isEqualTo("PENDING");
        assertThat(event(1).path("operation").asText()).isEqualTo("RECOVERY_BATCH");
        assertThat(event(0).path("id").asText()).isEqualTo(event(1).path("id").asText());
        assertThat(event(0).path("id").asText()).isNotEqualTo(event(2).path("id").asText());
        assertThat(IncidentTrace.recoveryAttempt(RECOVERY_APPROVAL)).isNull();
        verify(recovery, times(2)).reconcileDueCancellations(10);
    }
}
