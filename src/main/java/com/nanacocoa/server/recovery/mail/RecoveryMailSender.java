package com.nanacocoa.server.recovery.mail;

public interface RecoveryMailSender {
    void send(String recipient, String link);
}
