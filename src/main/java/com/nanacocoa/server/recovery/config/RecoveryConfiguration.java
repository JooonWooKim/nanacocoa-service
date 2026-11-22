package com.nanacocoa.server.recovery.config;

import com.nanacocoa.server.recovery.mail.*;
import java.net.URI;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration @EnableConfigurationProperties(RecoveryProperties.class)
@PropertySource("classpath:password-recovery.properties")
public class RecoveryConfiguration {
    @Bean Clock recoveryClock() { return Clock.systemUTC(); }

    @Bean(name = "recoveryExecutor")
    ThreadPoolTaskExecutor recoveryExecutor(RecoveryProperties p) {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2); executor.setMaxPoolSize(2);
        executor.setQueueCapacity(Math.max(1, p.getQueueCapacity()));
        executor.setThreadNamePrefix("password-recovery-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        return executor;
    }

    @Bean RecoveryMailSender recoveryMailSender(RecoveryProperties p, Environment environment) {
        boolean test = environment.acceptsProfiles(Profiles.of("test"));
        if (p.isEnabled()) {
            URI uri;
            try { uri = URI.create(p.getBaseUrl()); } catch (RuntimeException e) { throw invalidConfiguration(); }
            boolean local = p.isAllowLocalHttp() && test && "http".equals(uri.getScheme())
                    && ("localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost()));
            if (!("https".equals(uri.getScheme()) || local) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getFragment() != null || uri.getQuery() != null
                    || p.getTokenLifetime().isNegative() || p.getTokenLifetime().isZero()) throw invalidConfiguration();
        }
        if ("mock".equals(p.getMode())) {
            if (!test) throw invalidConfiguration();
            return new MockRecoveryMailSender();
        }
        if (!"smtp".equals(p.getMode())) throw invalidConfiguration();
        if (!p.isEnabled() || !p.isSmtpEnabled()) return (recipient, link) -> { throw invalidConfiguration(); };
        var s = p.getSmtp();
        if (s.getHost().isBlank() || s.getFrom().isBlank()) throw invalidConfiguration();
        boolean plaintext = s.isAllowPlaintextLoopback();
        if (plaintext && !(test && ("localhost".equals(s.getHost()) || "127.0.0.1".equals(s.getHost()))))
            throw invalidConfiguration();
        var sender = new JavaMailSenderImpl();
        sender.setHost(s.getHost()); sender.setPort(s.getPort());
        sender.setUsername(s.getUsername()); sender.setPassword(s.getPassword());
        var options = sender.getJavaMailProperties();
        options.setProperty("mail.smtp.connectiontimeout", "3000");
        options.setProperty("mail.smtp.timeout", "5000");
        options.setProperty("mail.smtp.writetimeout", "5000");
        options.setProperty("mail.smtp.starttls.enable", Boolean.toString(!plaintext));
        options.setProperty("mail.smtp.starttls.required", Boolean.toString(!plaintext));
        options.setProperty("mail.smtp.auth", Boolean.toString(!s.getUsername().isBlank()));
        return (recipient, link) -> {
            var message = new SimpleMailMessage();
            message.setFrom(s.getFrom()); message.setTo(recipient);
            message.setSubject("나나코코아 비밀번호 재설정");
            message.setText("비밀번호를 재설정하려면 아래 링크를 열어주세요. 요청하지 않았다면 무시하세요.\n" + link);
            sender.send(message);
        };
    }

    private IllegalStateException invalidConfiguration() {
        return new IllegalStateException("Invalid password recovery configuration");
    }
}
