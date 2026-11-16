package com.nanacocoa.server.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Map;
import java.util.regex.Pattern;

@Component
@ConfigurationProperties(prefix = "shipping")
public class ShippingCarriers {
    private Map<String, Carrier> carriers = Map.of();

    public Map<String, Carrier> getCarriers() {
        return carriers;
    }

    public void setCarriers(Map<String, Carrier> carriers) {
        carriers.forEach((code, carrier) -> {
            if (!code.matches("[a-z0-9-]{1,32}") || carrier.name() == null || carrier.name().isBlank()
                    || carrier.trackingPattern() == null || carrier.trackingPattern().isBlank()) {
                throw new IllegalArgumentException("Invalid shipping carrier configuration");
            }
            Pattern.compile(carrier.trackingPattern());
            if (carrier.trackingUrl() != null && !carrier.trackingUrl().isBlank()) {
                URI uri = URI.create(carrier.trackingUrl().replace("{tracking}", "nanacocoa-tracking-marker"));
                if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                        || uri.getUserInfo() != null || uri.getHost().contains("nanacocoa-tracking-marker")
                        || !carrier.trackingUrl().contains("{tracking}")) {
                    throw new IllegalArgumentException("Invalid shipping tracking URL configuration");
                }
            }
        });
        this.carriers = Map.copyOf(carriers);
    }

    public record Carrier(String name, String trackingPattern, String trackingUrl) {
        public boolean accepts(String number) {
            return number.matches("[A-Za-z0-9-]{1,64}") && number.matches(trackingPattern);
        }

        public String link(String number) {
            if (trackingUrl == null || trackingUrl.isBlank() || !accepts(number)) return null;
            return trackingUrl.replace("{tracking}", number);
        }
    }
}
