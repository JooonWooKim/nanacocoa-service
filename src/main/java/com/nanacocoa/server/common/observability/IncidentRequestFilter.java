package com.nanacocoa.server.common.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;

import static com.nanacocoa.server.common.observability.IncidentTrace.Operation;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class IncidentRequestFilter extends OncePerRequestFilter {
    private static final Pattern CANCELLATION = Pattern.compile("/api/payments/[^/]+/cancel");
    private static final Pattern RECONCILIATION = Pattern.compile("/api/admin/payment-operations/[^/]+/[^/]+/reconcile");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Operation operation = operation(request);
        if (operation == null) {
            chain.doFilter(request, response);
            return;
        }

        try (var trace = IncidentTrace.request(operation)) {
            response.setHeader("X-Request-Id", trace.id());
            try {
                chain.doFilter(request, response);
                trace.status(response.getStatus());
            } catch (RuntimeException | ServletException exception) {
                trace.status(500);
                IncidentTrace.mark(IncidentTrace.Result.INTERNAL_ERROR);
                // Keep the container's error handling while preventing it from logging the original payload.
                throw new ServletException("Incident request failed");
            } catch (IOException exception) {
                trace.status(500);
                IncidentTrace.mark(IncidentTrace.Result.INTERNAL_ERROR);
                throw new IOException("Incident request I/O failed");
            }
        }
    }

    private static Operation operation(HttpServletRequest request) {
        if (!request.getMethod().equals("POST")) return null;
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.equals("/api/orders")) return Operation.ORDER_CREATE;
        if (path.equals("/api/payments/confirm")) return Operation.PAYMENT_CONFIRM;
        if (CANCELLATION.matcher(path).matches()) return Operation.PAYMENT_CANCEL;
        if (RECONCILIATION.matcher(path).matches()) return Operation.PAYMENT_RECONCILE;
        return null;
    }
}
