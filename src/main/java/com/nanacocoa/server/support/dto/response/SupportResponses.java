package com.nanacocoa.server.support.dto.response;

import com.nanacocoa.server.support.entity.Inquiry;
import java.time.LocalDateTime;
import java.util.List;

public final class SupportResponses {
    private SupportResponses() {}
    public record Faq(String id, String question, String answer, String policyHref) {}
    public record Summary(Long id, String title, Inquiry.Status status, LocalDateTime createdAt) {
        public static Summary from(Inquiry i) { return new Summary(i.getId(), i.getTitle(), i.getStatus(), i.getCreatedAt()); }
    }
    public record Page(List<Summary> items, int page, boolean hasNext) {}
    public record Detail(Long id, String title, String body, String orderId, Inquiry.Status status,
                         LocalDateTime createdAt, String replyBody, LocalDateTime repliedAt) {
        public static Detail from(Inquiry i, String orderId) {
            return new Detail(i.getId(), i.getTitle(), i.getBody(), orderId, i.getStatus(),
                    i.getCreatedAt(), i.getReplyBody(), i.getRepliedAt());
        }
    }
    public record Receipt(Detail inquiry, boolean replayed) {}
}
