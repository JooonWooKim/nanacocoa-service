package com.nanacocoa.server.recovery.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter @Setter @ConfigurationProperties("recovery")
public class RecoveryProperties {
    private boolean enabled = false;
    private boolean smtpEnabled = false;
    private String mode = "smtp";
    private String baseUrl = "";
    private boolean allowLocalHttp = false;
    private Duration tokenLifetime = Duration.ofMinutes(30);
    private int queueCapacity = 32;
    private Smtp smtp = new Smtp();

    @Getter @Setter
    public static class Smtp {
        private boolean allowPlaintextLoopback = false;
        private String host = "";
        private int port = 587;
        private String username = "";
        private String password = "";
        private String from = "";
    }
}
