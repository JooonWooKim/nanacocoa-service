package com.nanacocoa.server.order.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import com.nanacocoa.server.common.exception.NanacocoaException;
import static com.nanacocoa.server.common.exception.ErrorCode.RETURN_POLICY_UNAVAILABLE;
import java.time.OffsetDateTime;

@Component
@ConfigurationProperties(prefix = "returns.policy")
@Getter
@Setter
public class ReturnPolicy {
    private boolean enabled;
    private String version = "";
    private String approvedBy = "";
    private String approvedAt = "";
    private String guidance = "";
    private int windowDays;
    private Boolean allowInTransit;
    private Boolean allowReapply;
    private Boolean refundAfterFailedInspection;

    public boolean available() {
        try {
            return enabled && !version.isBlank() && version.length() <= 64 && !approvedBy.isBlank()
                && OffsetDateTime.parse(approvedAt).isBefore(OffsetDateTime.now())
                && !guidance.isBlank() && windowDays > 0 && windowDays <= 3650
                && allowInTransit != null && allowReapply != null && refundAfterFailedInspection != null;
        } catch (RuntimeException invalid) { return false; }
    }
    public void requireAvailable() {
        if (!available()) throw new NanacocoaException(RETURN_POLICY_UNAVAILABLE);
    }
}
