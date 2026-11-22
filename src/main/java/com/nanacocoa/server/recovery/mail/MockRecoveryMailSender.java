package com.nanacocoa.server.recovery.mail;

import java.util.ArrayDeque;
import java.util.List;

/** 테스트 프로필의 메모리 수신함. 운영 API·로그로 노출하지 않는다. */
public class MockRecoveryMailSender implements RecoveryMailSender {
    public record Delivery(String recipient, String link) {
        @Override public String toString() { return "Delivery[redacted]"; }
    }
    private final ArrayDeque<Delivery> deliveries = new ArrayDeque<>();
    public synchronized void send(String recipient, String link) {
        if (deliveries.size() == 100) deliveries.removeFirst();
        deliveries.addLast(new Delivery(recipient, link));
    }
    public synchronized List<Delivery> deliveries() { return List.copyOf(deliveries); }
    public synchronized void clear() { deliveries.clear(); }
}
