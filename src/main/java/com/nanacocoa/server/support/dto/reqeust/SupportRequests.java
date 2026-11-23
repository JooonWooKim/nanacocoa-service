package com.nanacocoa.server.support.dto.reqeust;

public final class SupportRequests {
    private SupportRequests() {}
    public record Create(String title, String body, String orderId) {}
    public record Reply(String body) {}
}
