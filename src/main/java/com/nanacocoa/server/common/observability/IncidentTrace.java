package com.nanacocoa.server.common.observability;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class IncidentTrace {
    public enum Operation {
        ORDER_CREATE, PAYMENT_CONFIRM, PAYMENT_CANCEL, PAYMENT_RECONCILE,
        RECOVERY_BATCH, RECOVERY_APPROVAL, RECOVERY_COMPENSATION, RECOVERY_CANCELLATION
    }

    public enum Result {
        SUCCESS, PENDING, DEFERRED, FAILED, STOCK_CONFLICT, UNAUTHENTICATED, FORBIDDEN,
        CSRF_REJECTED, REQUEST_REJECTED, INTERNAL_ERROR
    }

    private static final Logger EVENTS = LoggerFactory.getLogger("nanacocoa.incident");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();

    private IncidentTrace() {}

    static Scope request(Operation operation) {
        return new Scope("HTTP", UUID.randomUUID(), operation, null);
    }

    public static Scope recoveryRun() {
        return new Scope("RECOVERY", UUID.randomUUID(), Operation.RECOVERY_BATCH, Result.INTERNAL_ERROR);
    }

    public static Scope recoveryAttempt(Operation operation) {
        Scope parent = CURRENT.get();
        return parent != null && parent.kind.equals("RECOVERY")
                ? new Scope(parent.kind, parent.id, operation, Result.DEFERRED) : null;
    }

    public static void mark(Result result) {
        Scope scope = CURRENT.get();
        if (scope != null) scope.result = result;
    }

    public static void markCompensation() {
        Scope scope = CURRENT.get();
        if (scope != null && scope.operation == Operation.RECOVERY_APPROVAL) {
            scope.operation = Operation.RECOVERY_COMPENSATION;
        }
    }

    public static final class Scope implements AutoCloseable {
        private final Scope previous = CURRENT.get();
        private final long started = System.nanoTime();
        private final String kind;
        private final UUID id;
        private Operation operation;
        private Result result;
        private Integer status;

        private Scope(String kind, UUID id, Operation operation, Result initialResult) {
            this.kind = kind;
            this.id = id;
            this.operation = operation;
            CURRENT.set(this);
            result = initialResult;
        }

        String id() { return id.toString(); }

        void status(int status) { this.status = status; }

        private Result result() {
            if (status != null && status >= 500) return Result.INTERNAL_ERROR;
            if (result != null && (status == null || status < 400 || result != Result.SUCCESS)) return result;
            if (status == null) return Result.INTERNAL_ERROR;
            return switch (status) {
                case 202 -> Result.PENDING;
                case 401 -> Result.UNAUTHENTICATED;
                case 403 -> Result.FORBIDDEN;
                default -> status >= 400 ? Result.REQUEST_REJECTED : Result.SUCCESS;
            };
        }

        @Override
        public void close() {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
            var fields = new LinkedHashMap<String, Object>();
            fields.put("kind", kind);
            fields.put("id", id);
            fields.put("occurredAt", Instant.now().toString());
            fields.put("operation", operation);
            fields.put("result", result());
            if (status != null) fields.put("httpStatus", status);
            fields.put("durationMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
            try {
                EVENTS.info("INCIDENT_EVENT {}", JSON.writeValueAsString(fields));
            } catch (JsonProcessingException ignored) {
                EVENTS.warn("INCIDENT_EVENT_SERIALIZATION_FAILED");
            }
        }
    }
}
